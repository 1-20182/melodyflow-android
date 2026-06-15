package com.melodyflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import com.melodyflow.app.R
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.data.MusicRepository
import com.melodyflow.app.model.MusicSource
import com.melodyflow.app.model.MusicSourceManager
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.UnplayableSongsHolder
import com.melodyflow.app.ui.PlayerActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class MusicService : MediaBrowserServiceCompat() {

    companion object {
        const val CHANNEL_ID = "melodyflow_playback_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_PLAY_SONG = "action_play_song"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"
        const val ACTION_SONG_CHANGED = "action_song_changed"
        private const val RETRY_DELAY_MS = 30 * 60 * 1000L // 30 minutes before allowing retry
    }

    interface PlaybackListener {
        fun onSongChanged(song: Song?)
        fun onPlayStateChanged(isPlaying: Boolean)
        fun onError(message: String)
        fun onSongCompleted() {}
    }

    private val playbackListeners = mutableListOf<PlaybackListener>()

    fun addPlaybackListener(listener: PlaybackListener) {
        if (!playbackListeners.contains(listener)) {
            playbackListeners.add(listener)
        }
    }

    fun removePlaybackListener(listener: PlaybackListener) {
        playbackListeners.remove(listener)
    }

    private fun notifySongChanged(song: Song?) {
        handler.post { playbackListeners.forEach { it.onSongChanged(song) } }
    }

    private fun notifyPlayStateChanged(isPlaying: Boolean) {
        handler.post { playbackListeners.forEach { it.onPlayStateChanged(isPlaying) } }
    }

    private fun notifyError(message: String) {
        handler.post { playbackListeners.forEach { it.onError(message) } }
    }

    private fun notifySongCompleted() {
        handler.post { playbackListeners.forEach { it.onSongCompleted() } }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayerPrepared = false
    private val handler = Handler(Looper.getMainLooper())
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var cacheManager: CacheManager
    private lateinit var musicRepository: MusicRepository

    private val albumArtCache = ConcurrentHashMap<String, Bitmap>()
    private var cachedNotificationSongId: String? = null
    private var cachedNotificationBitmap: Bitmap? = null

    inner class LocalBinder : android.os.Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        // Let MediaBrowserServiceCompat handle MediaBrowser connections (for Android Auto/TV)
        // Only use LocalBinder for internal app binding
        return if (intent?.action == MediaBrowserServiceCompat.SERVICE_INTERFACE) {
            super.onBind(intent)!!
        } else {
            binder
        }
    }

    private val playlist = mutableListOf<Song>()
    private var currentIndex = 0
    private var playMode = PlayMode.SEQUENCE
    private var playJob: Job? = null

    private var songFailCount = 0
    private val failedSongIds = mutableSetOf<String>()
    private val failedTimestamps = mutableMapOf<String, Long>()

    private var playbackVersion = 0

    enum class PlayMode {
        SEQUENCE, RANDOM, SINGLE, LOOP
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        cacheManager = CacheManager.getInstance(this)
        musicRepository = MusicRepository.getInstance(this)
        createNotificationChannel()
        initMediaSession()
    }

    private val musicCacheDir by lazy {
        java.io.File(applicationContext.cacheDir, "music_cache")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressRunnable)
        serviceJob.cancel()

        // Stop and clear old player callbacks before releasing
        mediaPlayer?.let { oldPlayer ->
            try {
                oldPlayer.setOnPreparedListener(null)
                oldPlayer.setOnCompletionListener(null)
                oldPlayer.setOnErrorListener(null)
                if (oldPlayer.isPlaying) {
                    oldPlayer.stop()
                }
                oldPlayer.reset()
                oldPlayer.release()
            } catch (e: Exception) {
                android.util.Log.w("MusicService", "Error releasing old player: ${e.message}")
            }
        }
        mediaPlayer = null
        isPlayerPrepared = false
        albumArtCache.clear()
        cachedNotificationBitmap = null
        cachedNotificationSongId = null
        
        // Clear all failed song tracking when service is destroyed
        // This prevents songs from being stuck in gray state after app exit
        val idsToRemove = failedSongIds.toList()
        failedSongIds.clear()
        failedTimestamps.clear()
        for (id in idsToRemove) {
            UnplayableSongsHolder.remove(id)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MelodyFlow").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { play() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
                override fun onStop() { stopSelf() }
            })
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PLAY_SONG -> {
                val song = intent.getParcelableExtra<Song>("song")
                val position = intent.getIntExtra("position", -1)
                if (song != null) {
                    playSong(song, position)
                }
            }
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    fun setPlaylist(songs: List<Song>, index: Int = 0) {
        playlist.clear()
        playlist.addAll(songs)
        currentIndex = index.coerceIn(0, playlist.size - 1)
    }

    fun addToPlaylist(song: Song) {
        if (!playlist.contains(song)) {
            playlist.add(song)
        }
    }

    fun playSong(song: Song, forceIndex: Int = -1) {
        resetSkipGuard()
        if (forceIndex in playlist.indices) {
            currentIndex = forceIndex
        } else {
            val index = playlist.indexOfFirst { it.id == song.id }
            if (index >= 0) {
                currentIndex = index
            } else {
                playlist.add(song)
                currentIndex = playlist.size - 1
            }
        }
        // Notify UI immediately about song change before starting playback
        notifySongChanged(getCurrentSong())
        playCurrent()
    }

    fun playCurrent() {
        playCurrentWithSource(null)
    }
    
    private fun playCurrentWithSource(forceSource: com.melodyflow.app.model.MusicSource?) {
        if (playlist.isEmpty() || currentIndex >= playlist.size) {
            resetPlayback()
            return
        }

        val song = playlist[currentIndex]

        // Skip songs that failed recently and haven't passed retry window
        if (failedSongIds.contains(song.id) && !shouldRetrySong(song.id)) {
            android.util.Log.i("MusicService", "Skipping failed song $song.id, retry window not passed")
            playNext()
            return
        }

        if (songFailCount >= playlist.size) {
            resetPlayback()
            songFailCount = 0
            failedSongIds.clear()
            failedTimestamps.clear()
            notifyPlayStateChanged(false)
            notifyError("播放失败：所有歌曲均无法播放")
            return
        }

        playbackVersion++
        val currentVersion = playbackVersion

        playJob?.cancel()
        playJob = null

        // Stop and clear old player callbacks before releasing
        mediaPlayer?.let { oldPlayer ->
            try {
                oldPlayer.setOnPreparedListener(null)
                oldPlayer.setOnCompletionListener(null)
                oldPlayer.setOnErrorListener(null)
                if (oldPlayer.isPlaying) {
                    oldPlayer.stop()
                }
                oldPlayer.reset()
                oldPlayer.release()
            } catch (e: Exception) {
                android.util.Log.w("MusicService", "Error releasing old player: ${e.message}")
            }
        }
        mediaPlayer = null
        isPlayerPrepared = false

        startForeground(NOTIFICATION_ID, buildBasicNotification(song))

        playJob = serviceScope.launch {
            try {
                // First check if song is already cached
                val cachedPath = cacheManager.getCachePath(song.id)
                if (cachedPath != null && java.io.File(cachedPath).exists()) {
                    android.util.Log.i("MusicService", "Playing cached file for ${song.id}")
                    startPlayback(song, cachedPath, true)
                    return@launch
                }

                // If song has a local file path, use it directly
                if (song.url != null && !song.url!!.startsWith("http") && java.io.File(song.url!!).exists()) {
                    android.util.Log.i("MusicService", "Playing local file for ${song.id} from ${song.url}")
                    startPlayback(song, song.url!!, true)
                    return@launch
                }

                // Get song URLs - try specific source or fallback to all sources
                android.util.Log.i("MusicService", "Fetching song URLs for ${song.id}...")
                val urls = if (forceSource != null) {
                    // Try specific source only
                    musicRepository.getSongUrlFromSource(song.id, forceSource)
                } else {
                    // Try all sources
                    musicRepository.getSongUrlCandidatesWithFallback(song.id)
                }
                android.util.Log.i("MusicService", "Got ${urls.size} URLs for ${song.id}: $urls")

                if (urls.isEmpty() || urls.all { it.isBlank() }) {
                    // If we haven't tried other sources yet, try switching
                    if (forceSource == null) {
                        val currentSource = com.melodyflow.app.model.MusicSourceManager.getCurrentSource()
                        val otherSource = com.melodyflow.app.model.MusicSourceManager.getOtherSource(currentSource)
                        android.util.Log.i("MusicService", "Trying other source: ${otherSource.server}")
                        // Retry with other source
                        playCurrentWithSource(otherSource)
                        return@launch
                    }
                    
                    android.util.Log.w("MusicService", "No URLs available for ${song.id}")
                    markSongFailed(song.id)
                    notifyError("无法获取歌曲链接")
                    // Don't call playNext() to avoid infinite loop
                    return@launch
                }

                // Try to stream directly from each URL until one succeeds (fast path)
                var selectedUrl = urls.firstOrNull() ?: ""
                
                if (selectedUrl.isBlank()) {
                    // If we haven't tried other sources yet, try switching
                    if (forceSource == null) {
                        val currentSource = com.melodyflow.app.model.MusicSourceManager.getCurrentSource()
                        val otherSource = com.melodyflow.app.model.MusicSourceManager.getOtherSource(currentSource)
                        android.util.Log.i("MusicService", "Trying other source: ${otherSource.server}")
                        playCurrentWithSource(otherSource)
                        return@launch
                    }
                    
                    android.util.Log.e("MusicService", "No valid URL for ${song.id}")
                    markSongFailed(song.id)
                    notifyError("无法播放: 没有可用链接")
                    // Don't call playNext() to avoid infinite loop
                    return@launch
                }

                android.util.Log.i("MusicService", "Starting streaming playback for ${song.id}: ${selectedUrl.take(50)}...")
                startPlayback(song, selectedUrl, false)
            } catch (e: CancellationException) {
                // Expected when playbackVersion changes
                android.util.Log.i("MusicService", "Playback cancelled (version change)")
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "playCurrent exception for ${song.id}", e)
                // Try other source on exception
                if (forceSource == null) {
                    val currentSource = com.melodyflow.app.model.MusicSourceManager.getCurrentSource()
                    val otherSource = com.melodyflow.app.model.MusicSourceManager.getOtherSource(currentSource)
                    android.util.Log.i("MusicService", "Exception occurred, trying other source: ${otherSource.server}")
                    markSongFailed(song.id)
                    playCurrentWithSource(otherSource)
                } else {
                    markSongFailed(song.id)
                    notifyError("播放错误: ${e.message}")
                }
            }
        }
    }

    private fun markSongFailed(songId: String) {
        // 暂时禁用歌曲变灰功能 - 只在内存中记录，不写入 SharedPreferences
        failedSongIds.add(songId)
        failedTimestamps[songId] = System.currentTimeMillis()
        songFailCount++
        android.util.Log.i("MusicService", "Song $songId marked as failed (in-memory only, not saved)")
    }
    
    private fun shouldRetrySong(songId: String): Boolean {
        val failedTime = failedTimestamps[songId] ?: return true
        val now = System.currentTimeMillis()
        val shouldRetry = now - failedTime > RETRY_DELAY_MS
        if (shouldRetry) {
            failedSongIds.remove(songId)
            failedTimestamps.remove(songId)
            UnplayableSongsHolder.remove(songId)
            android.util.Log.i("MusicService", "Retry window passed for $songId, allowing retry")
        }
        return shouldRetry
    }

    private fun resetPlayback() {
        playJob?.cancel()
        playJob = null

        // Stop and clear old player callbacks before releasing
        mediaPlayer?.let { oldPlayer ->
            try {
                oldPlayer.setOnPreparedListener(null)
                oldPlayer.setOnCompletionListener(null)
                oldPlayer.setOnErrorListener(null)
                if (oldPlayer.isPlaying) {
                    oldPlayer.stop()
                }
                oldPlayer.reset()
                oldPlayer.release()
            } catch (e: Exception) {
                android.util.Log.w("MusicService", "Error releasing old player: ${e.message}")
            }
        }
        mediaPlayer = null
        isPlayerPrepared = false
        songFailCount = 0
        failedSongIds.clear()
        notifyPlayStateChanged(false)
        stopForeground(false)
    }

    private fun resetSkipGuard() {
        // Save IDs to clear from SharedPreferences before clearing the set
        val idsToRemove = failedSongIds.toList()
        songFailCount = 0
        failedSongIds.clear()
        failedTimestamps.clear()
        // Also clear from UnplayableSongsHolder (SharedPreferences)
        for (id in idsToRemove) {
            UnplayableSongsHolder.remove(id)
        }
    }

    private suspend fun startPlayback(song: Song, path: String, isCached: Boolean) = withContext(Dispatchers.Main) {
        // Capture the version at the start of playback
        val versionAtStart = playbackVersion

        try {
            notifySongChanged(song)

            // Add to playback history
            serviceScope.launch(Dispatchers.IO) {
                try {
                    musicRepository.addToHistory(song)
                } catch (e: Exception) {
                    android.util.Log.w("MusicService", "Failed to add to history", e)
                }
            }

            isPlayerPrepared = false
            val player = MediaPlayer().apply {
                setOnPreparedListener {
                    // Check if this callback is still valid
                    if (playbackVersion != versionAtStart) {
                        android.util.Log.d("MusicService", "Ignoring onPrepared for old version")
                        return@setOnPreparedListener
                    }
                    isPlayerPrepared = true
                    start()
                    updateMetadata(song)
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    startForeground(NOTIFICATION_ID, buildNotification())
                    handler.post(updateProgressRunnable)
                    notifyPlayStateChanged(true)

                    // Remove from unplayable list if previously marked
                    UnplayableSongsHolder.remove(song.id)
                    // Also clear from service-level failed tracking
                    failedSongIds.remove(song.id)
                    failedTimestamps.remove(song.id)
                    songFailCount = maxOf(0, songFailCount - 1)

                    // 自动缓存：只缓存用户喜欢的歌曲
                    if (!isCached) {
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                // 检查歌曲是否在收藏列表中
                                val isFavorite = musicRepository.isFavoriteSync(song.id)
                                if (isFavorite) {
                                    android.util.Log.i("MusicService", "Auto-caching favorite song: ${song.name}")
                                    cacheManager.cacheSong(song.copy(url = path))
                                } else {
                                    android.util.Log.d("MusicService", "Skipping cache for non-favorite song: ${song.name}")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                setOnCompletionListener {
                    // Check if this callback is still valid
                    if (playbackVersion != versionAtStart) {
                        android.util.Log.d("MusicService", "Ignoring onCompletion for old version")
                        return@setOnCompletionListener
                    }
                    notifyPlayStateChanged(false)
                    notifySongCompleted()
                    when (playMode) {
                        PlayMode.SINGLE -> playCurrent()
                        else -> {
                            handler.postDelayed({
                                if (playbackVersion == versionAtStart) {
                                    playNext()
                                }
                            }, 300)
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    // Check if this callback is still valid
                    if (playbackVersion != versionAtStart) {
                        android.util.Log.d("MusicService", "Ignoring onError for old version")
                        return@setOnErrorListener true
                    }
                    android.util.Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra for song ${song.id}")
                    isPlayerPrepared = false
                    notifyError("播放错误 (what=$what, extra=$extra)")
                    markSongFailed(song.id)
                    // Try to play next song after error
                    handler.postDelayed({ playNext() }, 500)
                    true
                }
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }

            player.setDataSource(path)
            player.prepareAsync()
            mediaPlayer = player
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "startPlayback failed", e)
            notifyError("播放初始化失败: ${e.message}")
            markSongFailed(song.id)
            // Don't auto-skip, just mark as failed
            // User can manually click next to play the next song
        }
    }

    private fun buildBasicNotification(song: Song): Notification {
        val clickIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            this, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.name)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(clickPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startForeground(NOTIFICATION_ID, buildNotification())
                handler.post(updateProgressRunnable)
                notifyPlayStateChanged(true)
            }
        } ?: playCurrent()
    }

    private fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                stopForeground(false)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                handler.removeCallbacks(updateProgressRunnable)
                notifyPlayStateChanged(false)
            }
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) pause() else play()
        } ?: playCurrent()
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        when (playMode) {
            PlayMode.RANDOM -> {
                if (playlist.size > 1) {
                    var newIndex: Int
                    do { newIndex = (0 until playlist.size).random() } while (newIndex == currentIndex)
                    currentIndex = newIndex
                } else {
                    currentIndex = (currentIndex + 1) % playlist.size
                }
            }
            else -> currentIndex = (currentIndex + 1) % playlist.size
        }
        // Notify UI immediately about song change before starting playback
        notifySongChanged(getCurrentSong())
        playCurrent()
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        // Notify UI immediately about song change before starting playback
        notifySongChanged(getCurrentSong())
        playCurrent()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState(if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
    }

    fun setPlayMode(mode: PlayMode) { playMode = mode }
    fun getPlayMode(): PlayMode = playMode
    fun getCurrentSong(): Song? = if (playlist.isNotEmpty() && currentIndex < playlist.size) playlist[currentIndex] else null
    fun getPlaylist(): List<Song> = playlist.toList()

    fun playAt(index: Int) {
        if (index in playlist.indices) {
            currentIndex = index
            resetSkipGuard()
            // Notify UI immediately about song change before starting playback
            notifySongChanged(getCurrentSong())
            playCurrent()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int {
        if (!isPlayerPrepared) return 0
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }
    fun isPrepared(): Boolean = isPlayerPrepared

    private fun updateMetadata(song: Song) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0)
            .build()
        mediaSession.setMetadata(metadata)

        val cachedArt = albumArtCache[song.id]
        if (cachedArt != null) {
            cachedNotificationSongId = song.id
            cachedNotificationBitmap = cachedArt
            val updatedMetadata = MediaMetadataCompat.Builder(metadata)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cachedArt)
                .build()
            mediaSession.setMetadata(updatedMetadata)
        } else {
            loadAlbumArt(song.pic) { bitmap ->
                if (bitmap != null) {
                    albumArtCache[song.id] = bitmap
                    cachedNotificationSongId = song.id
                    cachedNotificationBitmap = bitmap
                }
                val updatedMetadata = MediaMetadataCompat.Builder(metadata)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    .build()
                mediaSession.setMetadata(updatedMetadata)
            }
        }
    }

    private fun loadAlbumArt(url: String, callback: (Bitmap?) -> Unit) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val future = Glide.with(this@MusicService)
                    .asBitmap()
                    .load(url)
                    .submit(256, 256)
                val bitmap = future.get()
                withContext(Dispatchers.Main) { callback(bitmap) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(null) }
            }
        }
    }

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, position, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildNotification(): Notification {
        val currentSong = getCurrentSong()
        val isPlaying = isPlaying()

        val clickIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(this, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val previousIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS }
        val previousPendingIntent = PendingIntent.getService(this, 3, previousIntent, PendingIntent.FLAG_IMMUTABLE)

        val largeIcon: Bitmap? = if (currentSong != null && cachedNotificationSongId == currentSong.id) {
            cachedNotificationBitmap
        } else {
            albumArtCache[currentSong?.id]
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.name ?: "MelodyFlow")
            .setContentText(currentSong?.artist ?: "Unknown Artist")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(largeIcon)
            .setContentIntent(clickPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_previous, "Previous", previousPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }
}