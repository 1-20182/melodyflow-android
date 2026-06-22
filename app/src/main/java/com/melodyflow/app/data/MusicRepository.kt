package com.melodyflow.app.data

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.melodyflow.app.db.*
import com.melodyflow.app.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.security.Key
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Exception) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}

class MusicRepository private constructor(context: Context) {

    private val db = MusicDatabase.getInstance(context)
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()
    private val aiConfigDao = db.aiConfigDao()
    private val aiRecommendationDao = db.aiRecommendationDao()
    private val userConversationDao = db.userConversationDao()
    private val cacheManager = CacheManager.getInstance(context)
    private val gson = Gson()

    // ============================================================
    // Pending playlist state (replaces SongListHolder static variable)
    // ============================================================

    private val _pendingPlaylist = MutableStateFlow<List<Song>?>(null)
    val pendingPlaylistFlow: StateFlow<List<Song>?> = _pendingPlaylist.asStateFlow()

    fun setPendingPlaylist(songs: List<Song>?) {
        _pendingPlaylist.value = songs
    }

    fun getPendingPlaylist(): List<Song>? = _pendingPlaylist.value

    init {
        MusicSourceManager.init(context)
    }

    private val chartCache = mutableMapOf<String, Pair<List<Song>, Long>>()
    private val chartCacheValidityMs = 5 * 60 * 1000L

    private val networkSemaphore = Semaphore(5)

    private val client: OkHttpClient

    private val retrofit: Retrofit

    private val api: MusicApiService

    // Meting API Token - 默认使用 "token"
    private val METING_TOKEN = "token"

    init {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        val httpCacheDir = File(context.cacheDir, "http_cache")
        httpCacheDir.mkdirs()
        clientBuilder.cache(Cache(httpCacheDir, 10 * 1024 * 1024L))

        clientBuilder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Cache-Control", "public, max-age=300")
                .header("Referer", "https://y.music.163.com/")
                .build()
            chain.proceed(requestWithHeaders)
        }

        clientBuilder.addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )

        client = clientBuilder.build()

        // 使用公共 Meting API
        retrofit = Retrofit.Builder()
            .baseUrl("https://api.qijieya.cn/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(MusicApiService::class.java)
    }

    companion object {
        @Volatile
        private var INSTANCE: MusicRepository? = null
        private val ID_FROM_URL = Regex("id=(\\d+)")

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun extractIdFromUrl(url: String): String? {
            return ID_FROM_URL.find(url)?.groupValues?.getOrNull(1)
        }

        /**
         * 生成 Meting API 认证 Token
         * Token 计算公式: HMAC-SHA1(METING_TOKEN, server + type + id)
         */
        fun generateAuthToken(token: String, server: String, type: String, id: String): String {
            return try {
                val message = "$server$type$id"
                val keySpec = SecretKeySpec(token.toByteArray(), "HmacSHA1")
                val mac = Mac.getInstance("HmacSHA1")
                mac.init(keySpec)
                val hmacBytes = mac.doFinal(message.toByteArray())

                // 转换为十六进制字符串
                hmacBytes.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                // 如果 HMAC 计算失败，返回空字符串
                android.util.Log.e("MusicRepo", "Failed to generate auth token", e)
                ""
            }
        }
    }

    /**
     * 生成指定音乐源的认证 Token
     */
    private fun generateAuthToken(server: String, type: String, id: String): String {
        return generateAuthToken(METING_TOKEN, server, type, id)
    }

    // ============================================================
    // Search
    // ============================================================

    suspend fun search(keyword: String, source: MusicSource? = null): ApiResult<List<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                // Search both sources and merge results
                val results = mutableListOf<Song>()
                val seenIds = mutableSetOf<String>()
                
                // Search NetEase
                val neteaseResults = api.search(
                    server = MusicSource.NETEASE.server,
                    keyword = keyword
                )
                android.util.Log.i("MusicRepo", "NetEase search returned ${neteaseResults.size} results")
                neteaseResults.forEach {
                    val song = it.toSong(MusicSource.NETEASE)
                    if (!seenIds.contains(song.id)) {
                        seenIds.add(song.id)
                        results.add(song)
                    }
                }
                
                // Search QQ Music
                val qqResults = api.search(
                    server = MusicSource.QQ.server,
                    keyword = keyword
                )
                android.util.Log.i("MusicRepo", "QQ Music search returned ${qqResults.size} results")
                qqResults.forEach {
                    val song = it.toSong(MusicSource.QQ)
                    if (!seenIds.contains(song.id)) {
                        seenIds.add(song.id)
                        results.add(song)
                    }
                }
                
                android.util.Log.i("MusicRepo", "Total merged results: ${results.size}")
                ApiResult.Success(results)
            } catch (e: Exception) {
                android.util.Log.e("MusicRepo", "Search failed", e)
                ApiResult.Error(e)
            }
        }
    }

    suspend fun searchArtist(query: String, source: MusicSource? = null): ApiResult<List<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentSource = source ?: MusicSourceManager.getCurrentSource()
                val results = api.searchArtist(
                    server = currentSource.server,
                    keyword = query
                ).map { it.toSong(currentSource) }
                ApiResult.Success(results)
            } catch (e: Exception) {
                android.util.Log.e("MusicRepo", "Search artist failed", e)
                ApiResult.Error(e)
            }
        }
    }

    suspend fun searchAlbum(query: String, source: MusicSource? = null): ApiResult<List<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentSource = source ?: MusicSourceManager.getCurrentSource()
                val results = api.searchAlbum(
                    server = currentSource.server,
                    keyword = query
                ).map { it.toSong(currentSource) }
                ApiResult.Success(results)
            } catch (e: Exception) {
                android.util.Log.e("MusicRepo", "Search album failed", e)
                ApiResult.Error(e)
            }
        }
    }

    // ============================================================
    // Song URL from Meting API
    // ============================================================

    suspend fun getSongUrl(id: String, source: MusicSource? = null): String? {
        return withContext(Dispatchers.IO) {
            val currentSource = source ?: MusicSourceManager.getCurrentSource()
            try {
                android.util.Log.i("MusicRepo", "Getting URL for song ID: $id from ${currentSource.server}")
                
                // 使用 getSongInfo 获取歌曲详情，其中包含 URL 字段
                val results = api.getSongInfo(
                    server = currentSource.server,
                    id = id
                )
                
                val result = results.firstOrNull()
                val url = result?.url
                
                if (!url.isNullOrBlank()) {
                    android.util.Log.i("MusicRepo", "Got URL for $id: $url")
                    url.replace("\\/", "/")
                } else {
                    android.util.Log.w("MusicRepo", "No URL found for $id in song info")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicRepo", "Failed to get URL for $id from ${currentSource.server}", e)
                // 尝试使用备用方法获取URL
                getSongUrlAlternative(id, currentSource)
            }
        }
    }
    
    /**
     * Alternative method to get song URL using the URL API endpoint directly.
     * This handles cases where getSongInfo returns different formats.
     */
    private suspend fun getSongUrlAlternative(id: String, source: MusicSource): String? {
        return try {
            android.util.Log.i("MusicRepo", "Trying alternative URL method for $id from ${source.server}")
            val response = api.getUrl(server = source.server, id = id)
            if (!response.isNullOrBlank()) {
                android.util.Log.i("MusicRepo", "Alternative method got URL for $id: ${response.take(50)}...")
                response.replace("\\/", "/")
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicRepo", "Alternative URL method failed for $id: ${e.message}")
            null
        }
    }

    // Try both sources for song URL
    suspend fun getSongUrlWithFallback(id: String): Pair<String?, MusicSource> {
        val currentSource = MusicSourceManager.getCurrentSource()
        
        // First try current source
        var url = getSongUrl(id, currentSource)
        if (!url.isNullOrBlank()) {
            return url to currentSource
        }
        
        // Fallback to other source
        val otherSource = MusicSourceManager.getOtherSource(currentSource)
        url = getSongUrl(id, otherSource)
        return url to if (!url.isNullOrBlank()) otherSource else currentSource
    }

    /**
     * Get song details by song ID using getSongInfo API.
     * This uses the same API that gets song URLs, which returns full song details.
     */
    suspend fun getSongDetailById(songId: String, source: MusicSource? = null): Song? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.i("MusicRepo", "Getting song detail for ID: $songId")
                
                val currentSource = source ?: MusicSourceManager.getCurrentSource()
                
                // Try using getSongInfo API (same as getting song URL)
                val results = api.getSongInfo(
                    server = currentSource.server,
                    id = songId
                )
                android.util.Log.i("MusicRepo", "API returned ${results.size} results")
                
                val result = results.firstOrNull()
                
                if (result != null && result.title.isNotBlank()) {
                    android.util.Log.i("MusicRepo", "Got song from API: title=${result.title}, artist=${result.artist}, author=${result.author}, pic=${result.pic}")
                    
                    // 从 URL 中提取歌曲 ID
                    val extractedId = extractIdFromUrl(result.url) ?: songId
                    
                    return@withContext Song(
                        id = extractedId,
                        name = result.title,
                        artist = result.artist.ifBlank { result.author.ifBlank { "未知艺术家" } },
                        album = result.album,
                        pic = result.pic,
                        url = result.url,
                        lrc = result.lrc.ifBlank { null },
                        duration = result.duration,
                        isCached = false,
                        source = currentSource.name
                    )
                }
                
                android.util.Log.w("MusicRepo", "Could not get song detail for $songId")
                
                // 如果当前源没有结果，尝试另一个源
                val otherSource = MusicSourceManager.getOtherSource(currentSource)
                val fallbackResults = api.getSongInfo(
                    server = otherSource.server,
                    id = songId
                )
                
                val fallbackResult = fallbackResults.firstOrNull()
                if (fallbackResult != null && fallbackResult.title.isNotBlank()) {
                    android.util.Log.i("MusicRepo", "Got song from fallback source ${otherSource.server}: title=${fallbackResult.title}")
                    val extractedId = extractIdFromUrl(fallbackResult.url) ?: songId
                    
                    return@withContext Song(
                        id = extractedId,
                        name = fallbackResult.title,
                        artist = fallbackResult.artist.ifBlank { fallbackResult.author.ifBlank { "未知艺术家" } },
                        album = fallbackResult.album,
                        pic = fallbackResult.pic,
                        url = fallbackResult.url,
                        lrc = fallbackResult.lrc.ifBlank { null },
                        duration = fallbackResult.duration,
                        isCached = false,
                        source = otherSource.name
                    )
                }
                
                null
            } catch (e: Exception) {
                android.util.Log.e("MusicRepo", "Error getting song detail for $songId", e)
                null
            }
        }
    }

    /**
     * Get multiple candidate URLs for a song.
     * First tries Meting API proxy URL, then Netease direct URL as fallback.
     */
    suspend fun getSongUrlCandidates(id: String, source: MusicSource? = null): List<String> {
        val currentSource = source ?: MusicSourceManager.getCurrentSource()
        val urls = mutableListOf<String>()
        val metingUrl = getSongUrl(id, currentSource)
        if (!metingUrl.isNullOrBlank()) urls.add(metingUrl)
        return urls
    }

    /**
     * Get song URL candidates with fallback - optimized for speed
     * Try current source first, if fails try other source
     */
    suspend fun getSongUrlCandidatesWithFallback(id: String): List<String> {
        val urls = mutableListOf<String>()
        val startTime = System.currentTimeMillis()
        
        val currentSource = MusicSourceManager.getCurrentSource()
        val otherSource = MusicSourceManager.getOtherSource(currentSource)
        
        // Try current source first - fastest
        val currentUrl = getSongUrlWithRetry(id, currentSource)
        if (!currentUrl.isNullOrBlank()) {
            urls.add(currentUrl)
        }
        
        // Only try other source if current failed
        if (urls.isEmpty()) {
            val otherUrl = getSongUrlWithRetry(id, otherSource)
            if (!otherUrl.isNullOrBlank()) {
                urls.add(otherUrl)
            }
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.i("MusicRepo", "getSongUrlCandidatesWithFallback for $id: ${urls.size} URLs found in ${elapsed}ms")
        return urls
    }
    
    /**
     * Get song URL from a specific source only
     */
    suspend fun getSongUrlFromSource(id: String, source: MusicSource): List<String> {
        val urls = mutableListOf<String>()
        val startTime = System.currentTimeMillis()
        
        val url = getSongUrlWithRetry(id, source)
        if (!url.isNullOrBlank()) {
            urls.add(url)
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.i("MusicRepo", "getSongUrlFromSource for $id from ${source.server}: ${urls.size} URLs found in ${elapsed}ms")
        return urls
    }
    
    /**
     * Get song URL with retry support - optimized for speed
     */
    private suspend fun getSongUrlWithRetry(id: String, source: MusicSource, maxRetries: Int = 0): String? {
        var lastError: String? = null
        
        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    android.util.Log.i("MusicRepo", "Retry ${attempt} for song $id from ${source.server}")
                    delay(100L) // Fast retry
                }
                val url = getSongUrl(id, source)
                if (!url.isNullOrBlank()) {
                    return url
                }
            } catch (e: Exception) {
                lastError = e.message
                android.util.Log.w("MusicRepo", "getSongUrlWithRetry attempt ${attempt} failed for $id from ${source.server}: ${e.message}")
            }
        }
        
        android.util.Log.w("MusicRepo", "All ${maxRetries + 1} attempts failed for $id from ${source.server}: $lastError")
        return null
    }

    /**
     * Download a song to a local file. Returns true if successful (file > 1KB and not HTML).
     * Optimized for speed with minimal retries.
     */
    suspend fun downloadSongToFile(url: String, outputFile: java.io.File, maxRetries: Int = 0): Boolean = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        
        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    android.util.Log.i("MusicRepo", "Retry download attempt ${attempt} for ${outputFile.name}")
                    delay(200L) // Fast retry
                }
                
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w("MusicRepo", "Download attempt ${attempt} failed: HTTP ${response.code} for $url")
                        lastError = Exception("HTTP ${response.code}")
                        return@withContext false
                    }
                    
                    if (response.body == null) {
                        android.util.Log.w("MusicRepo", "Download attempt ${attempt}: Empty response body for $url")
                        lastError = Exception("Empty response body")
                        return@withContext false
                    }
                    
                    val contentType = response.header("Content-Type") ?: ""
                    if (contentType.contains("text/html") || contentType.contains("application/json")) {
                        android.util.Log.w("MusicRepo", "Download attempt ${attempt}: Invalid content type $contentType for $url")
                        lastError = Exception("Invalid content type: $contentType")
                        return@withContext false
                    }
                    
                    val body = response.body!!
                    val contentLength = body.contentLength()
                    
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    
                    val fileSize = outputFile.length()
                    val minSize = if (contentLength > 0) (contentLength * 0.1).toLong().coerceAtLeast(1024) else 1024
                    
                    if (fileSize < minSize) {
                        android.util.Log.w("MusicRepo", "Download attempt ${attempt}: File too small ($fileSize bytes) for $url")
                        lastError = Exception("File too small: $fileSize bytes")
                        outputFile.delete()
                        return@withContext false
                    }
                    
                    android.util.Log.i("MusicRepo", "Download successful: ${outputFile.name} (${fileSize} bytes)")
                    return@withContext true
                }
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.w("MusicRepo", "Download attempt ${attempt} timed out: ${e.message}")
                lastError = e
                outputFile.delete()
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.w("MusicRepo", "Download attempt ${attempt}: Network error (Unknown host): ${e.message}")
                lastError = e
                outputFile.delete()
            } catch (e: Exception) {
                android.util.Log.w("MusicRepo", "Download attempt ${attempt} failed: ${e.message}")
                lastError = e
                outputFile.delete()
            }
        }
        
        android.util.Log.e("MusicRepo", "All download attempts failed for $url: ${lastError?.message}")
        false
    }

    // ============================================================
    // Song info
    // ============================================================

    suspend fun getSongInfo(id: String, source: MusicSource? = null): ApiResult<Song?> {
        return withContext(Dispatchers.IO) {
            try {
                val currentSource = source ?: MusicSourceManager.getCurrentSource()
                val results = api.getSongInfo(
                    server = currentSource.server,
                    id = id
                )
                val song = results.firstOrNull()?.toSong(currentSource)
                ApiResult.Success(song)
            } catch (e: Exception) {
                android.util.Log.e("MusicRepo", "getSongInfo failed", e)
                ApiResult.Error(e)
            }
        }
    }

    // ============================================================
    // Lyrics
    // ============================================================

    suspend fun getLyrics(id: String, source: MusicSource? = null, existingLrc: String? = null): String? {
        if (!existingLrc.isNullOrBlank()) {
            return existingLrc
        }

        val currentSource = source ?: MusicSourceManager.getCurrentSource()
        
        val metingResult = try {
            val rawJson = api.getLrc(
                server = currentSource.server,
                id = id
            )
            parseLrcFromMeting(rawJson)
        } catch (e: Exception) { 
            android.util.Log.e("MusicRepo", "Failed to get lyrics from ${currentSource.server}", e)
            null 
        }
        
        if (!metingResult.isNullOrBlank()) return metingResult
        
        // Try other source as fallback
        val otherSource = MusicSourceManager.getOtherSource(currentSource)
        return try {
            val rawJson = api.getLrc(
                server = otherSource.server,
                id = id
            )
            parseLrcFromMeting(rawJson)
        } catch (e: Exception) {
            android.util.Log.e("MusicRepo", "Failed to get lyrics from ${otherSource.server}", e)
            null
        }
    }

    private fun parseLrcFromMeting(rawJson: String): String? {
        return try {
            // 如果是纯文本歌词（直接返回的 LRC 格式）
            if (rawJson.trimStart().startsWith("[") && rawJson.contains("]")) {
                return rawJson.trim()
            }
            
            // 尝试解析为 JSON 数组
            val type = object : TypeToken<List<LyricResponse>>() {}.type
            val responses: List<LyricResponse> = gson.fromJson(rawJson, type)
            for (resp in responses) {
                val text = if (resp.lrc.isNotBlank()) resp.lrc else resp.lyric
                if (text.isNotBlank()) {
                    return text
                        .replace("\\/", "/")
                        .replace("\\n", "\n")
                        .replace("\\\\n", "\n")
                }
            }
            if (rawJson.contains("[") && rawJson.contains("]")) {
                rawJson
            } else null
        } catch (e: Exception) {
            android.util.Log.e("MusicRepo", "Failed to parse lyrics", e)
            // 尝试直接返回，可能是纯文本歌词
            if (rawJson.trimStart().startsWith("[") && rawJson.contains("]")) {
                rawJson.trim()
            } else null
        }
    }

    /**
     * Check if URL is accessible (fast HEAD request)
     */
    suspend fun isUrlAccessible(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicRepo", "URL check failed for ${url.take(50)}: ${e.message}")
            // If HEAD fails, try to use URL anyway - let MediaPlayer decide
            true
        }
    }

    // ============================================================
    // Chart / Playlist
    // ============================================================

    suspend fun getPlaylist(id: String, source: MusicSource? = null): List<Song> {
        val cached = chartCache[id]
        if (cached != null && System.currentTimeMillis() - cached.second < chartCacheValidityMs) {
            return cached.first
        }
        return try {
            val currentSource = source ?: MusicSourceManager.getCurrentSource()
            val songs = withContext(Dispatchers.IO) {
                api.getPlaylist(
                    server = currentSource.server,
                    id = id
                ).map { it.toSong(currentSource) }
            }
            chartCache[id] = Pair(songs, System.currentTimeMillis())
            songs
        } catch (e: Exception) {
            e.printStackTrace()
            cached?.first ?: emptyList()
        }
    }

    suspend fun getChart(server: String, id: String): List<Song> = getPlaylist(id)

    // ============================================================
    // Favorites
    // ============================================================

    fun getFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    suspend fun addFavorite(song: Song) {
        favoriteDao.insert(
            FavoriteEntity(
                id = song.id,
                name = song.name,
                artist = song.artist,
                album = song.album,
                pic = song.pic,
                url = song.url
            )
        )
    }

    suspend fun removeFavorite(songId: String) {
        favoriteDao.deleteById(songId)
    }

    fun isFavorite(songId: String): Flow<Boolean> = favoriteDao.isFavorite(songId)

    suspend fun isFavoriteSync(songId: String): Boolean {
        return favoriteDao.isFavoriteSync(songId)
    }

    suspend fun clearFavorites() {
        favoriteDao.clearAll()
    }

    // ============================================================
    // History
    // ============================================================

    fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getAll()

    suspend fun addToHistory(song: Song) {
        val now = System.currentTimeMillis()
        historyDao.insert(
            HistoryEntity(
                id = song.id,
                name = song.name,
                artist = song.artist,
                album = song.album,
                pic = song.pic,
                url = song.url,
                playedAt = now,
                playCount = 1,
                lastPlayedAt = now
            )
        )
        // 如果歌曲已存在（insert 被 IGNORE），则更新播放统计
        historyDao.updatePlayStats(song.id, now)
        historyDao.trimOldRecords()
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    // ============================================================
    // Playlist / Import
    // ============================================================

    suspend fun getPlaylistSongs(playlistId: String, source: MusicSource? = null): List<Song> {
        return try {
            withContext(Dispatchers.IO) {
                val currentSource = source ?: MusicSourceManager.getCurrentSource()
                api.getPlaylist(
                    server = currentSource.server,
                    id = playlistId
                ).map { it.toSong(currentSource) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun parsePlaylistUrl(url: String): String? {
        if (url.isBlank()) return null
        val regex = Regex("[?&]id=(\\d+)")
        val match = regex.find(url)
        return match?.groupValues?.getOrNull(1)
    }

    suspend fun batchSearchAndFavorite(
        names: List<String>,
        onProgress: (Int, Int) -> Unit
    ): List<Pair<String, Song?>> {
        return coroutineScope {
            val semaphore = Semaphore(5)
            names.mapIndexed { index, name ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        onProgress(index + 1, names.size)
                        try {
                            val songs = api.search(keyword = name)
                            val song = songs.firstOrNull()
                            if (song != null) {
                                val s = song.toSong(MusicSourceManager.getCurrentSource())
                                addFavorite(s)
                                name to s
                            } else {
                                name to null
                            }
                        } catch (e: Exception) {
                            name to null
                        }
                    }
                }
            }.awaitAll()
        }
    }

    // ============================================================
    // Charts data
    // ============================================================

    fun getCharts(): List<Chart> {
        val currentSource = MusicSourceManager.getCurrentSource()
        return when (currentSource) {
            MusicSource.NETEASE -> listOf(
                Chart("3778678", "热歌榜", "netease"),
                Chart("3779629", "新歌榜", "netease"),
                Chart("19723756", "飙升榜", "netease"),
                Chart("2884035", "原创榜", "netease"),
                Chart("60198", "说唱榜", "netease"),
                Chart("180106", "DJ榜", "netease")
            )
            MusicSource.QQ -> listOf(
                Chart("4", "热歌榜", "tencent"),
                Chart("26", "新歌榜", "tencent"),
                Chart("27", "飙升榜", "tencent"),
                Chart("62", "华语榜", "tencent"),
                Chart("5", "流行指数榜", "tencent"),
                Chart("3", "巅峰榜", "tencent")
            )
        }
    }

    // ============================================================
    // SearchResult -> Song conversion
    // ============================================================

    // 预编译正则表达式，避免重复创建
    private val ID_PATTERN = Regex("id=(\\d+)")
    
    private fun extractIdFromUrlCached(url: String): String? {
        return ID_PATTERN.find(url)?.groupValues?.getOrNull(1)
    }

    private fun SearchResult.toSong(source: MusicSource): Song {
        // Extract song ID from URL, fallback to title hash if not found
        val songId = extractIdFromUrlCached(url) ?: 
            if (title.isNotBlank()) "song_${title.hashCode()}" 
            else "song_${System.nanoTime()}"
        
        // Use artist field, fallback to author field if empty
        val artistName = if (artist.isNotBlank()) artist else author
        
        return Song(
            id = songId,
            name = title,
            artist = artistName,
            album = album,
            pic = pic,
            url = url,
            lrc = if (lrc.isBlank()) null else lrc,
            duration = duration,
            isCached = false,
            source = source.name
        )
    }

    // ============================================================
    // AI Configuration
    // ============================================================

    suspend fun getAIConfig(): AIConfig? {
        return aiConfigDao.getConfig()?.toAIConfig()
    }

    fun getAIConfigFlow(): Flow<AIConfig?> {
        return aiConfigDao.getConfigFlow().map { it?.toAIConfig() }
    }

    suspend fun saveAIConfig(config: AIConfig) {
        aiConfigDao.saveConfig(config.toEntity())
    }

    suspend fun clearAIConfig() {
        aiConfigDao.clearConfig()
    }

    private fun AIConfigEntity.toAIConfig(): AIConfig {
        return AIConfig(
            provider = AIProvider.fromString(provider),
            apiUrl = apiUrl,
            apiKey = apiKey,
            model = model,
            isEnabled = isEnabled
        )
    }

    private fun AIConfig.toEntity(): AIConfigEntity {
        return AIConfigEntity(
            provider = provider.name,
            apiUrl = apiUrl,
            apiKey = apiKey,
            model = model,
            isEnabled = isEnabled
        )
    }

    // ============================================================
    // AI Recommendations
    // ============================================================

    fun getAIRecommendations(): Flow<List<AIRecommendationSession>> {
        return aiRecommendationDao.getAllRecommendations().map { entities ->
            entities.map { it.toSession() }
        }
    }

    suspend fun saveAIRecommendation(session: AIRecommendationSession): Long {
        return aiRecommendationDao.insertRecommendation(session.toEntity())
    }

    suspend fun markRecommendationAsPlayed(id: Long) {
        aiRecommendationDao.updatePlayedStatus(id, true)
    }

    suspend fun deleteAIRecommendation(id: Long) {
        aiRecommendationDao.deleteRecommendation(id)
    }

    suspend fun clearAIRecommendations() {
        aiRecommendationDao.clearAllRecommendations()
    }

    private fun AIRecommendationEntity.toSession(): AIRecommendationSession {
        val songs = try {
            gson.fromJson(songsJson, Array<AIRecommendedSong>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
        return AIRecommendationSession(
            id = id,
            timestamp = timestamp,
            userPrompt = userPrompt,
            playlistName = playlistName,
            explanation = explanation,
            songs = songs,
            isPlayed = isPlayed
        )
    }

    private fun AIRecommendationSession.toEntity(): AIRecommendationEntity {
        return AIRecommendationEntity(
            id = id,
            timestamp = timestamp,
            userPrompt = userPrompt,
            playlistName = playlistName,
            explanation = explanation,
            songsJson = gson.toJson(songs),
            isPlayed = isPlayed
        )
    }

    // ============================================================
    // User Conversations
    // ============================================================

    fun getUserConversations(limit: Int = 50): Flow<List<UserConversation>> {
        return userConversationDao.getRecentConversations(limit).map { entities ->
            entities.map { it.toConversation() }
        }
    }

    suspend fun saveUserConversation(conversation: UserConversation): Long {
        return userConversationDao.insertConversation(conversation.toEntity())
    }

    private fun UserConversationEntity.toConversation(): UserConversation {
        return UserConversation(
            id = id,
            timestamp = timestamp,
            userMessage = userMessage,
            aiResponse = aiResponse,
            type = ConversationType.valueOf(type)
        )
    }

    private fun UserConversation.toEntity(): UserConversationEntity {
        return UserConversationEntity(
            id = id,
            timestamp = timestamp,
            userMessage = userMessage,
            aiResponse = aiResponse,
            type = type.name
        )
    }
}