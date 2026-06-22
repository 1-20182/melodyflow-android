package com.melodyflow.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.melodyflow.app.db.CacheDao
import com.melodyflow.app.db.CacheEntity
import com.melodyflow.app.db.MusicDatabase
import com.melodyflow.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CacheManager private constructor(context: Context) {

    private val cacheDao: CacheDao = MusicDatabase.getInstance(context).cacheDao()
    private val externalBaseDir = File(Environment.getExternalStorageDirectory(), "MelodyFlow")
    private val cacheDir = File(externalBaseDir, "cache/music")
    private val picCacheDir = File(externalBaseDir, "cache/pic")
    private val lrcCacheDir = File(externalBaseDir, "cache/lrc")
    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)  // 禁用自动重定向跟随，手动处理
        .build()

    // LRU bitmap cache: key = url hash, value = Bitmap
    private val bitmapLruCache: LruCache<String, Bitmap>

    companion object {
        @Volatile
        private var INSTANCE: CacheManager? = null

        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /** Calculate a reasonable inSampleSize so that the decoded bitmap uses at most maxPixels. */
        private fun calculateInSampleSize(
            options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
        ): Int {
            val (height, width) = options.outHeight to options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }

    init {
        cacheDir.mkdirs()
        picCacheDir.mkdirs()
        lrcCacheDir.mkdirs()

        // Check if migration from internal cache is needed
        val prefs = appContext.getSharedPreferences("melodyflow_cache", 0)
        if (!prefs.getBoolean("migrated_to_external", false)) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    migrateFromInternalCache()
                    prefs.edit().putBoolean("migrated_to_external", true).apply()
                } catch (e: Exception) {
                    // Migration failed, continue with external dir anyway
                    android.util.Log.e("CacheManager", "Migration from internal cache failed", e)
                }
            }
        }

        // Use 1/8 of available app memory for bitmap LRU cache
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() // in KB
        val cacheSize = maxMemory / 8
        bitmapLruCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                // Return size in KB
                return bitmap.allocationByteCount / 1024
            }
        }
    }

    // ============================================================
    // Storage permission & migration
    // ============================================================

    /**
     * Check if the app has storage permission to write to external public directory.
     * On Android R+ (API 30+), MANAGE_EXTERNAL_STORAGE is required.
     * On older versions, READ/WRITE_EXTERNAL_STORAGE is sufficient.
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // For older versions, READ/WRITE_EXTERNAL_STORAGE is sufficient
        }
    }

    /**
     * Migrate cache files from old internal storage to external public directory.
     * Moves music, picture, and lyrics files, then updates DB records with new paths.
     * Returns true if any files were migrated.
     */
    suspend fun migrateFromInternalCache(): Boolean {
        val oldMusicDir = File(appContext.cacheDir, "music_cache")
        val oldPicDir = File(appContext.cacheDir, "pic_cache")
        val oldLrcDir = File(appContext.cacheDir, "lrc_cache")

        var migrated = false

        // Migrate music files
        migrated = migrateDirectory(oldMusicDir, cacheDir) || migrated

        // Migrate picture files
        migrated = migrateDirectory(oldPicDir, picCacheDir) || migrated

        // Migrate lyrics files
        migrated = migrateDirectory(oldLrcDir, lrcCacheDir) || migrated

        // Update DB records with new file paths
        if (migrated) {
            updateDbPathsAfterMigration()
        }

        return migrated
    }

    /**
     * Migrate files from an old directory to a new directory.
     * Moves each file individually; skips if target already exists.
     * Deletes the old directory if it becomes empty.
     */
    private fun migrateDirectory(oldDir: File, newDir: File): Boolean {
        if (!oldDir.exists()) return false

        var migrated = false
        newDir.mkdirs()

        oldDir.listFiles()?.forEach { file ->
            val target = File(newDir, file.name)
            if (!target.exists()) {
                try {
                    file.copyTo(target, overwrite = false)
                    if (target.exists()) {
                        file.delete()
                        migrated = true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CacheManager", "Failed to migrate ${file.name}", e)
                }
            } else {
                // Target already exists, just delete the old file
                file.delete()
                migrated = true
            }
        }

        // Delete old directory if empty
        if (oldDir.listFiles()?.isEmpty() != false) {
            oldDir.delete()
        }

        return migrated
    }

    /**
     * Update database records to point to the new external storage paths
     * after migration from internal cache.
     */
    private suspend fun updateDbPathsAfterMigration() {
        try {
            val allCaches = cacheDao.getAllOnce()
            for (cache in allCaches) {
                var updated = false
                var newFilePath = cache.filePath
                var newPic = cache.pic
                var newLrc = cache.lrc

                // Update music file path
                if (cache.filePath.contains("music_cache")) {
                    val fileName = File(cache.filePath).name
                    newFilePath = File(cacheDir, fileName).absolutePath
                    updated = true
                }

                // Update picture path
                if (cache.pic.contains("pic_cache")) {
                    val fileName = File(cache.pic).name
                    newPic = File(picCacheDir, fileName).absolutePath
                    updated = true
                }

                // Update lyrics path
                if (cache.lrc.contains("lrc_cache")) {
                    val fileName = File(cache.lrc).name
                    newLrc = File(lrcCacheDir, fileName).absolutePath
                    updated = true
                }

                if (updated) {
                    cacheDao.insert(
                        cache.copy(
                            filePath = newFilePath,
                            pic = newPic,
                            lrc = newLrc
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Failed to update DB paths after migration", e)
        }
    }

    // ============================================================
    // Bitmap LRU cache helpers
    // ============================================================

    /**
     * Load a bitmap with sampling (inSampleSize) to avoid OOM on large images.
     * Results are cached in the LRU memory cache.
     */
    suspend fun loadBitmap(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        // Check LRU cache first
        val cacheKey = url
        bitmapLruCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) return@withContext null

                val bytes = response.body!!.bytes()

                // First decode with inJustDecodeBounds=true to read dimensions
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565 // Save memory

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                bitmap?.let { bitmapLruCache.put(cacheKey, it) }
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Load a bitmap without sampling (full size). Cached in LRU.
     */
    suspend fun loadBitmap(url: String): Bitmap? {
        val cacheKey = url
        bitmapLruCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) return@withContext null

                val bytes = response.body!!.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap?.let { bitmapLruCache.put(cacheKey, it) }
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun evictBitmapFromCache(url: String) {
        bitmapLruCache.remove(url)
    }

    fun clearBitmapCache() {
        bitmapLruCache.evictAll()
    }

    // ============================================================
    // Song cache
    // ============================================================

    fun getCachedSongs(): Flow<List<CacheEntity>> = cacheDao.getAll()

    suspend fun getCacheEntity(songId: String): CacheEntity? {
        return withContext(Dispatchers.IO) {
            cacheDao.getBySongId(songId)
        }
    }

    suspend fun isCached(songId: String): Boolean {
        return withContext(Dispatchers.IO) {
            cacheDao.getBySongId(songId) != null
        }
    }

    suspend fun getCachePath(songId: String): String? {
        return withContext(Dispatchers.IO) {
            cacheDao.getBySongId(songId)?.filePath
        }
    }

    suspend fun cacheSong(song: Song, progressCallback: ((Int) -> Unit)? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if already cached
                val existingCache = cacheDao.getBySongId(song.id)
                if (existingCache != null) {
                    android.util.Log.i("CacheManager", "Song ${song.id} already cached")
                    return@withContext true
                }

                // If URL is null, try to resolve it via MusicRepository
                var url = song.url
                if (url.isNullOrBlank()) {
                    android.util.Log.i("CacheManager", "Song ${song.id} has no URL, trying to resolve via API")
                    try {
                        val repository = MusicRepository.getInstance(appContext)
                        url = repository.getSongUrl(song.id)
                        if (url.isNullOrBlank()) {
                            android.util.Log.w("CacheManager", "Could not resolve URL for song ${song.id}, skipping")
                            return@withContext false
                        }
                        android.util.Log.i("CacheManager", "Resolved URL for song ${song.id}: $url")
                    } catch (e: Exception) {
                        android.util.Log.w("CacheManager", "Failed to resolve URL for song ${song.id}: ${e.message}")
                        return@withContext false
                    }
                }
                android.util.Log.i("CacheManager", "Starting download for ${song.name} (ID: ${song.id})")
                android.util.Log.i("CacheManager", "URL: $url")
                
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://music.163.com/")
                    .header("Accept", "*/*")
                    .build()
                val response = client.newCall(request).execute()

                android.util.Log.i("CacheManager", "Response code: ${response.code}")
                android.util.Log.i("CacheManager", "Response headers: ${response.headers}")
                
                // Check if response is a redirect
                if (response.code == 302 || response.code == 301) {
                    val location = response.header("Location")
                    android.util.Log.i("CacheManager", "Redirect to: $location")
                    if (location != null) {
                        // Follow the redirect manually to get the actual URL
                        val redirectRequest = Request.Builder().url(location)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("Accept", "*/*")
                            .build()
                        val redirectResponse = client.newCall(redirectRequest).execute()
                        android.util.Log.i("CacheManager", "Redirect response code: ${redirectResponse.code}")
                        
                        if (!redirectResponse.isSuccessful || redirectResponse.body == null) {
                            android.util.Log.e("CacheManager", "Redirect request failed: ${redirectResponse.code}")
                            return@withContext false
                        }
                        
                        return@withContext downloadToFile(redirectResponse, song, progressCallback)
                    }
                }

                if (!response.isSuccessful || response.body == null) {
                    android.util.Log.e("CacheManager", "Request failed: ${response.code}")
                    return@withContext false
                }
                
                // Check content type
                val contentType = response.header("Content-Type") ?: ""
                android.util.Log.i("CacheManager", "Content-Type: $contentType")
                
                // If content is JSON, it means the URL is not a direct audio URL
                if (contentType.contains("application/json") || contentType.contains("text/")) {
                    android.util.Log.e("CacheManager", "Received JSON/text instead of audio data")
                    // Try to parse the URL from the response
                    val bodyText = response.body!!.string()
                    android.util.Log.i("CacheManager", "Response body: ${bodyText.take(200)}")
                    return@withContext false
                }

                return@withContext downloadToFile(response, song, progressCallback)
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Download failed for ${song.id}", e)
                false
            }
        }
    }
    
    private suspend fun downloadToFile(
        response: okhttp3.Response,
        song: Song,
        progressCallback: ((Int) -> Unit)? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val body = response.body!!
                val contentLength = body.contentLength()
                android.util.Log.i("CacheManager", "Content length: $contentLength bytes")
                
                val outputFile = File(cacheDir, "${song.id}.mp3")
                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0

                body.source().use { source ->
                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            progressCallback?.invoke(progress)
                        }
                    }
                }
                outputStream.close()

                android.util.Log.i("CacheManager", "Downloaded ${outputFile.length()} bytes to ${outputFile.absolutePath}")

                // Cache picture if available
                val picPath = if (song.pic.isNotBlank() && song.pic.startsWith("http")) {
                    try {
                        cachePicture(song.id, song.pic)
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                // Cache lyrics if available
                val lrcPath = if (!song.lrc.isNullOrBlank() && song.lrc.startsWith("http")) {
                    try {
                        cacheLyrics(song.id, song.lrc)
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                cacheDao.insert(
                    CacheEntity(
                        songId = song.id,
                        songName = song.name,
                        artist = song.artist,
                        album = song.album,
                        pic = picPath,
                        lrc = lrcPath,
                        filePath = outputFile.absolutePath,
                        fileSize = outputFile.length()
                    )
                )
                android.util.Log.i("CacheManager", "Cache entry created for ${song.id}")

                // Enforce cache limit after adding new cache entry
                enforceCacheLimit()

                true
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Failed to save file for ${song.id}", e)
                false
            }
        }
    }

    private suspend fun cachePicture(songId: String, picUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(picUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    return@withContext ""
                }

                val outputFile = File(picCacheDir, "${songId}.jpg")
                val outputStream = FileOutputStream(outputFile)
                response.body!!.byteStream().use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                outputFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    private suspend fun cacheLyrics(songId: String, lrcUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(lrcUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    return@withContext ""
                }

                val outputFile = File(lrcCacheDir, "${songId}.lrc")
                val outputStream = FileOutputStream(outputFile)
                response.body!!.byteStream().use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                outputFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    suspend fun removeCache(songId: String) {
        withContext(Dispatchers.IO) {
            val cache = cacheDao.getBySongId(songId)
            cache?.let {
                // Delete audio file
                val audioFile = File(it.filePath)
                if (audioFile.exists()) {
                    audioFile.delete()
                }
                // Delete picture file
                if (it.pic.isNotBlank()) {
                    val picFile = File(it.pic)
                    if (picFile.exists()) {
                        picFile.delete()
                    }
                }
                // Delete lyrics file
                if (it.lrc.isNotBlank()) {
                    val lrcFile = File(it.lrc)
                    if (lrcFile.exists()) {
                        lrcFile.delete()
                    }
                }
                cacheDao.deleteBySongId(songId)
            }
        }
    }

    /**
     * Clear all cached songs using parallel file deletion.
     */
    suspend fun clearAllCache() {
        withContext(Dispatchers.IO) {
            try {
                val allCachesList = cacheDao.getAll().first()
                coroutineScope {
                    allCachesList.map { cache ->
                        async {
                            // Delete audio file
                            val audioFile = File(cache.filePath)
                            if (audioFile.exists()) {
                                audioFile.delete()
                            }
                            // Delete picture file
                            if (cache.pic.isNotBlank()) {
                                val picFile = File(cache.pic)
                                if (picFile.exists()) {
                                    picFile.delete()
                                }
                            }
                            // Delete lyrics file
                            if (cache.lrc.isNotBlank()) {
                                val lrcFile = File(cache.lrc)
                                if (lrcFile.exists()) {
                                    lrcFile.delete()
                                }
                            }
                        }
                    }.awaitAll()
                }
                cacheDao.clearAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Parallel cleanup of orphaned files: removes files from disk that no longer have DB entries,
     * and removes DB entries whose files no longer exist.
     */
    suspend fun parallelCleanup() {
        withContext(Dispatchers.IO) {
            try {
                val allCachesList = cacheDao.getAll().first()
                coroutineScope {
                    // Remove music_cache files whose DB entry is gone
                    val dbSongIds = allCachesList.map { it.songId }.toSet()
                    val filesOnDisk = cacheDir.listFiles() ?: emptyArray()
                    filesOnDisk.filter { file ->
                        val songId = file.nameWithoutExtension
                        songId !in dbSongIds
                    }.map { file ->
                        async { file.delete() }
                    }.awaitAll()

                    // Remove pic_cache orphaned files (no DB entry references them via pic field)
                    val dbPicPaths = allCachesList.map { it.pic }.filter { it.isNotBlank() }.toSet()
                    val picFilesOnDisk = picCacheDir.listFiles() ?: emptyArray()
                    picFilesOnDisk.filter { file ->
                        file.absolutePath !in dbPicPaths
                    }.map { file ->
                        async { file.delete() }
                    }.awaitAll()

                    // Remove lrc_cache orphaned files (no DB entry references them via lrc field)
                    val dbLrcPaths = allCachesList.map { it.lrc }.filter { it.isNotBlank() }.toSet()
                    val lrcFilesOnDisk = lrcCacheDir.listFiles() ?: emptyArray()
                    lrcFilesOnDisk.filter { file ->
                        file.absolutePath !in dbLrcPaths
                    }.map { file ->
                        async { file.delete() }
                    }.awaitAll()

                    // Remove DB entries whose file is gone
                    allCachesList.filter { cache ->
                        !File(cache.filePath).exists()
                    }.map { cache ->
                        async { cacheDao.deleteBySongId(cache.songId) }
                    }.awaitAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getCacheCount(): Int {
        return withContext(Dispatchers.IO) {
            cacheDao.getCount()
        }
    }

    suspend fun getCacheSize(): Long {
        return withContext(Dispatchers.IO) {
            cacheDao.getTotalSize()
        }
    }

    /**
     * Count songs that need info completion.
     */
    suspend fun countNeedsCompletion(): Int {
        return withContext(Dispatchers.IO) {
            cacheDao.countNeedsCompletion()
        }
    }

    // ============================================================
    // Intelligent cache capacity management
    // ============================================================

    /**
     * Enforce cache size limit by evicting oldest non-favorite caches first.
     * Reads the cache limit from shared preferences (default 500 MB).
     * Favorite songs are protected from eviction.
     */
    suspend fun enforceCacheLimit() {
        withContext(Dispatchers.IO) {
            try {
                val limitMb = appContext.getSharedPreferences("melodyflow_settings", 0)
                    .getInt("cache_limit_mb", 500)
                val limitBytes = limitMb * 1024L * 1024L

                val totalSize = cacheDao.getTotalSize()
                if (totalSize <= limitBytes) return@withContext

                // Get all favorite song IDs to protect them from eviction
                val favoriteIds = MusicDatabase.getInstance(appContext)
                    .favoriteDao().getAllFavoriteIds()

                // Get non-favorite caches sorted by cachedAt (oldest first)
                val nonFavoriteCaches = cacheDao.getNonFavoriteCachesOldestFirst(favoriteIds)

                var freedBytes = 0L
                val needToFree = totalSize - limitBytes

                for (cache in nonFavoriteCaches) {
                    if (freedBytes >= needToFree) break
                    freedBytes += cache.fileSize
                    removeCache(cache.songId)
                    android.util.Log.i("CacheManager", "Evicted cache for ${cache.songId} (${cache.fileSize} bytes) to enforce limit")
                }

                if (freedBytes < needToFree && nonFavoriteCaches.isEmpty()) {
                    // All caches are favorites but still over limit; evict oldest favorites as last resort
                    val allCaches = cacheDao.getAllOnce().sortedBy { it.cachedAt }
                    for (cache in allCaches) {
                        if (freedBytes >= needToFree) break
                        freedBytes += cache.fileSize
                        removeCache(cache.songId)
                        android.util.Log.i("CacheManager", "Evicted favorite cache for ${cache.songId} as last resort")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Failed to enforce cache limit", e)
            }
        }
    }

    // ============================================================
    // Cache metadata consistency check
    // ============================================================

    /**
     * Verify cache consistency between database records and disk files.
     * 1. Removes DB records whose files no longer exist on disk (ghost entries).
     * 2. Removes orphaned files on disk that have no corresponding DB record.
     */
    suspend fun verifyCacheConsistency() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Find DB records without files (ghost entries)
                val allCaches = cacheDao.getAllOnce()
                for (cache in allCaches) {
                    val musicFile = File(cache.filePath)
                    if (!musicFile.exists()) {
                        cacheDao.deleteBySongId(cache.songId)
                        android.util.Log.i("CacheManager", "Removed ghost DB entry for ${cache.songId}")
                    }
                }

                // 2. Find orphaned files in music, pic, lrc directories
                // Re-fetch after ghost entry cleanup to get accurate path set
                val remainingCaches = cacheDao.getAllOnce()
                val allCachedPaths = remainingCaches.flatMap { cache ->
                    listOfNotNull(
                        cache.filePath,
                        cache.pic.ifBlank { null },
                        cache.lrc.ifBlank { null }
                    )
                }.toSet()

                for (dir in listOf(cacheDir, picCacheDir, lrcCacheDir)) {
                    dir.listFiles()?.forEach { file ->
                        if (file.absolutePath !in allCachedPaths) {
                            file.delete()
                            android.util.Log.i("CacheManager", "Removed orphaned file: ${file.absolutePath}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Failed to verify cache consistency", e)
            }
        }
    }

    /**
     * Scan disk for cached files and restore them to database.
     * This is useful after database migration when cache records are lost,
     * or after app reinstall when external storage still contains cached files.
     */
    suspend fun restoreCachedFilesFromDisk(): Int {
        return withContext(Dispatchers.IO) {
            try {
                var restoredCount = 0
                
                // Scan music cache directory
                val musicFiles = cacheDir.listFiles()?.filter { 
                    it.extension == "mp3" && it.length() > 0 
                } ?: emptyList()
                
                for (file in musicFiles) {
                    val songId = file.nameWithoutExtension
                    
                    // Check if already in database
                    if (cacheDao.getBySongId(songId) != null) continue
                    
                    // Find corresponding picture file
                    val picFile = File(picCacheDir, "${songId}.jpg")
                    val picPath = if (picFile.exists()) picFile.absolutePath else ""
                    
                    // Find corresponding lyrics file
                    val lrcFile = File(lrcCacheDir, "${songId}.lrc")
                    val lrcPath = if (lrcFile.exists()) lrcFile.absolutePath else ""
                    
                    // Check if songId looks like a valid Netease ID (all digits)
                    val isValidNeteaseId = songId.matches(Regex("\\d+"))
                    
                    // Create cache entity
                    // If it's a valid Netease ID and no local info, mark as needing completion
                    // If it's not a valid ID, mark as completed to avoid failed API calls
                    cacheDao.insert(
                        CacheEntity(
                            songId = songId,
                            songName = if (isValidNeteaseId) "未知歌曲" else "未知歌曲($songId)",
                            artist = if (isValidNeteaseId) "未知艺术家" else "未知艺术家",
                            album = "",
                            pic = picPath,
                            lrc = lrcPath,
                            filePath = file.absolutePath,
                            fileSize = file.length(),
                            infoCompleted = !isValidNeteaseId  // Non-Netease IDs skip API
                        )
                    )
                    restoredCount++
                }
                
                restoredCount
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    /**
     * Complete missing cache info (song name, artist, cover, lyrics) for cached songs.
     * Uses Meting API to fetch song details.
     * Only processes songs where infoCompleted is false to avoid duplicate API calls.
     */
    suspend fun completeMissingCacheInfo(progressCallback: ((current: Int, total: Int) -> Unit)? = null): Int {
        return withContext(Dispatchers.IO) {
            try {
                val repository = MusicRepository.getInstance(appContext)
                val allCaches = cacheDao.getAll().first()
                
                // Filter caches that need completion (infoCompleted is false OR name is unknown)
                val needsCompletion = allCaches.filter { 
                    !it.infoCompleted || it.songName == "未知歌曲" || it.artist == "未知艺术家" || it.pic.isBlank()
                }
                
                var completedCount = 0
                val total = needsCompletion.size
                
                for ((index, cache) in needsCompletion.withIndex()) {
                    // Report progress
                    progressCallback?.invoke(index + 1, total)
                    
                    try {
                        // Fetch song details from API
                        val songDetail = repository.getSongDetailById(cache.songId)
                        
                        if (songDetail != null && songDetail.name.isNotBlank()) {
                            android.util.Log.i("CacheManager", "Got song detail: ${songDetail.name} by ${songDetail.artist}, pic: ${songDetail.pic}")
                            
                            // Determine new values
                            val newSongName = songDetail.name
                            val newArtist = songDetail.artist.ifBlank { "未知艺术家" }
                            val newAlbum = songDetail.album.ifBlank { cache.album }
                            
                            // Determine cover URL to use
                            var coverUrl = songDetail.pic
                            
                            // Cache cover if missing
                            var newPicPath = cache.pic
                            if (newPicPath.isBlank() && coverUrl.isNotBlank()) {
                                android.util.Log.i("CacheManager", "Caching cover for ${cache.songId}: $coverUrl")
                                newPicPath = cachePicture(cache.songId, coverUrl)
                                if (newPicPath.isBlank()) {
                                    android.util.Log.e("CacheManager", "Failed to cache cover for ${cache.songId}")
                                }
                            }
                            
                            // Cache lyrics if missing
                            var newLrcPath = cache.lrc
                            if (newLrcPath.isBlank() && !songDetail.lrc.isNullOrBlank()) {
                                newLrcPath = cacheLyrics(cache.songId, songDetail.lrc)
                            }
                            
                            // Update cache entry with infoCompleted = true
                            val updatedCache = cache.copy(
                                songName = newSongName,
                                artist = newArtist,
                                album = newAlbum,
                                pic = newPicPath,
                                lrc = newLrcPath,
                                infoCompleted = true
                            )
                            cacheDao.insert(updatedCache)
                            completedCount++
                            
                            android.util.Log.i("CacheManager", "Completed info for ${cache.songId}: $newSongName by $newArtist")
                        } else {
                            android.util.Log.w("CacheManager", "Could not fetch details for song: ${cache.songId}")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // Small delay to avoid overwhelming the API
                    kotlinx.coroutines.delay(100)
                }
                
                completedCount
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    /**
     * Backup cache metadata to a JSON file (without audio files).
     * Returns the file path on success, null on failure.
     */
    suspend fun backupCacheMetadata(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val allCaches = cacheDao.getAll().first()
                
                // Create backup data (without filePath for security)
                val backupData = allCaches.map { cache ->
                    mapOf(
                        "songId" to cache.songId,
                        "songName" to cache.songName,
                        "artist" to cache.artist,
                        "album" to cache.album,
                        "pic" to cache.pic,
                        "lrc" to cache.lrc,
                        "fileSize" to cache.fileSize,
                        "cachedAt" to cache.cachedAt,
                        "infoCompleted" to cache.infoCompleted
                    )
                }
                
                val gson = Gson()
                val json = gson.toJson(backupData)
                
                // Save to Downloads folder
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "melodyflow_cache_backup_${dateFormat.format(Date())}.json"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupFile = File(downloadsDir, fileName)
                
                FileOutputStream(backupFile).use { fos ->
                    fos.write(json.toByteArray(Charsets.UTF_8))
                }
                
                android.util.Log.i("CacheManager", "Backup saved to: ${backupFile.absolutePath}")
                backupFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Restore cache metadata from a backup file.
     * If audio files are missing, they will be automatically re-downloaded.
     */
    suspend fun restoreFromBackup(backupFilePath: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(backupFilePath)
                if (!file.exists()) {
                    android.util.Log.e("CacheManager", "Backup file not found: $backupFilePath")
                    return@withContext 0
                }
                
                val json = file.readText()
                val gson = Gson()
                val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                val backupData: List<Map<String, Any?>> = gson.fromJson(json, type)
                
                var restoredCount = 0
                
                for (data in backupData) {
                    val songId = data["songId"] as? String ?: continue
                    val songName = data["songName"] as? String ?: "未知歌曲"
                    val artist = data["artist"] as? String ?: "未知艺术家"
                    val album = data["album"] as? String ?: ""
                    val pic = data["pic"] as? String ?: ""
                    val lrc = data["lrc"] as? String ?: ""
                    val fileSize = (data["fileSize"] as? Number)?.toLong() ?: 0L
                    val cachedAt = (data["cachedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val infoCompleted = data["infoCompleted"] as? Boolean ?: false
                    
                    // Check if already in database
                    val existing = cacheDao.getBySongId(songId)
                    if (existing != null) {
                        android.util.Log.i("CacheManager", "Song $songId already exists in database")
                        restoredCount++
                        continue
                    }
                    
                    // Check if audio file still exists
                    val audioFile = File(cacheDir, "$songId.mp3")
                    if (audioFile.exists()) {
                        // Audio file exists, just restore metadata
                        cacheDao.insert(
                            CacheEntity(
                                songId = songId,
                                songName = songName,
                                artist = artist,
                                album = album,
                                pic = pic,
                                lrc = lrc,
                                filePath = audioFile.absolutePath,
                                fileSize = fileSize,
                                cachedAt = cachedAt,
                                infoCompleted = infoCompleted
                            )
                        )
                        restoredCount++
                        android.util.Log.i("CacheManager", "Restored from existing file: $songName by $artist")
                    } else {
                        // Audio file not found, try to re-download
                        android.util.Log.i("CacheManager", "Audio file not found for $songId, trying to re-download")
                        // url is left null intentionally; cacheSong() will resolve it via MusicRepository API
                        val song = com.melodyflow.app.model.Song(
                            id = songId,
                            name = songName,
                            artist = artist,
                            album = album,
                            pic = pic ?: "",
                            url = null,
                            lrc = lrc ?: "",
                            source = "netease"
                        )
                        if (cacheSong(song)) {
                            restoredCount++
                            android.util.Log.i("CacheManager", "Re-downloaded and restored: $songName by $artist")
                        } else {
                            android.util.Log.w("CacheManager", "Failed to re-download: $songName")
                        }
                    }
                }
                
                restoredCount
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    /**
     * Scan for backup files in Downloads folder and restore all found backups.
     */
    suspend fun scanAndRestoreBackups(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupFiles = downloadsDir.listFiles()?.filter { 
                    it.name.startsWith("melodyflow_cache_backup_") && it.name.endsWith(".json")
                } ?: emptyList()
                
                var totalRestored = 0
                
                for (file in backupFiles) {
                    val count = restoreFromBackup(file.absolutePath)
                    totalRestored += count
                }
                
                totalRestored
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    /**
     * Get list of available backup files.
     */
    fun getAvailableBackups(): List<String> {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.listFiles()?.filter { 
                it.name.startsWith("melodyflow_cache_backup_") && it.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() }?.map { it.absolutePath } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
