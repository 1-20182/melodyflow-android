package com.melodyflow.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.LyricAdapter
import com.melodyflow.app.model.LyricLine
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.SongListHolder
import com.melodyflow.app.service.MusicService
import com.melodyflow.app.util.BackgroundManager
import com.melodyflow.app.util.Logger
import com.melodyflow.app.util.LyricParser
import com.melodyflow.app.util.LyricsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnLyrics: ImageButton
    private var btnMore: ImageButton? = null
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private var seekBar: android.widget.SeekBar? = null
    private var slider: Slider? = null
    private lateinit var btnPlayMode: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPlaylist: ImageButton
    private var cardContainer: FrameLayout? = null
    // Cover views
    private lateinit var coverContainer: View
    private lateinit var coverCard: com.google.android.material.card.MaterialCardView
    private lateinit var ivCover: ImageView
    private lateinit var coverPlayOverlay: View
    private lateinit var btnCoverPlay: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    // Background blur
    private var bgBlurImage: ImageView? = null
    // Lyrics preview (landscape mode)
    private var lyricsPreviewContainer: View? = null
    private var rvLyricsPreview: RecyclerView? = null
    private var tvNoLyricsPreview: TextView? = null
    // Lyrics overlay (fullscreen)
    private lateinit var lyricsFocusOverlay: View
    private lateinit var btnCloseLyrics: ImageButton
    private lateinit var rvLyricsFocus: RecyclerView
    private lateinit var tvNoLyricsFocus: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var musicService: MusicService? = null
    private var serviceBound = false
    private var currentSong: Song? = null
    private var pendingPlaylist: List<Song>? = null
    private var clickIndexExtra: Int = -1

    private var retainedSong: Song? = null
    private var retainedIsPlaying = false

    // Sleep timer
    private var sleepTimerHandler: Handler? = null
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerEndTime: Long = 0
    private var isSleepTimerActive = false
    private var sleepTimerMode: SleepTimerMode = SleepTimerMode.OFF
    private var remainingSongsCount: Int = 0  // 剩余需要播放的歌曲数量

    enum class SleepTimerMode {
        OFF,           // 未开启
        FIXED_TIME,    // 固定时长（15/30/60分钟）
        END_OF_SONG,   // 播放完当前歌曲
        AFTER_SONGS    // 播放完指定数量的歌曲
    }

    // Cover rotation animation
    private var rotationAnimator: ValueAnimator? = null
    private var isPlaying = false

    private lateinit var gestureDetector: android.view.GestureDetector

    // Swipe gesture debounce
    private var lastSwipeTime = 0L
    private val swipeCooldownMs = 300L
    private var isLyricsTransitioning = false

    private val repository by lazy {
        (application as MelodyFlowApp).repository
    }

    private var isLyricsFocusMode = false
    private var lyricsList = listOf<LyricLine>()
    private var currentLyricIndex = -1
    private lateinit var lyricsFocusAdapter: LyricsFocusAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // #region debug-point service-connected
            Logger.d("PlayerActivity", "[player-cover-landscape-bug] onServiceConnected START")
            // #endregion
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
            musicService?.addPlaybackListener(playbackListener)
            // #region debug-point before-initPlayer
            Logger.d("PlayerActivity", "[player-cover-landscape-bug] Calling initPlayer from onServiceConnected")
            // #endregion
            initPlayer()
            // #region debug-point after-initPlayer
            Logger.d("PlayerActivity", "[player-cover-landscape-bug] initPlayer completed")
            // #endregion
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.removePlaybackListener(playbackListener)
            musicService = null
            serviceBound = false
        }
    }

    private val playbackListener = object : MusicService.PlaybackListener {
        override fun onSongChanged(song: Song?) {
            Logger.d("PlayerActivity", "onSongChanged: song=${song?.name}")
            song?.let {
                currentSong = it
                retainedSong = it
                updateCoverView(it)
                loadLyrics(it)
                // Reset progress bar when song changes
                setProgressBarValue(0)
                tvCurrentTime.text = "0:00"
                tvTotalTime.text = "0:00"
                // Reset lyrics position
                lyricsFocusAdapter?.setCurrentIndex(-1)
                rvLyricsFocus.scrollToPosition(0)
            }
        }

        override fun onPlayStateChanged(isPlaying: Boolean) {
            retainedIsPlaying = isPlaying
            updatePlayButtonState(isPlaying)
            updateCoverAnimation(isPlaying)
        }

        override fun onError(message: String) {
            Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_SHORT).show()
        }

        override fun onSongCompleted() {
            this@PlayerActivity.onSongCompleted()
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            musicService?.let { service ->
                try {
                    val position = service.getCurrentPosition()
                    val duration = service.getDuration()

                    // Update progress bar max when duration is valid
                    if (duration > 0) {
                        setProgressBarMax(duration)
                        tvTotalTime.text = formatTime(duration)
                    }

                    // Always update position and UI if we have valid position
                    if (duration > 0 || position > 0) {
                        setProgressBarValue(position.coerceIn(0, duration.coerceAtLeast(0)))
                        tvCurrentTime.text = formatTime(position)
                        updateLyricsFocusPosition(position)
                    }

                    updatePlayButtonState(service.isPlaying())
                } catch (e: Exception) {
                    // Player not ready yet, skip this update
                    Logger.d("PlayerActivity", "Progress update skipped: player not ready")
                }
            }
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // #region debug-point onCreate-start
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] onCreate START, savedInstanceState=$savedInstanceState, retainedSong=$retainedSong")
        // #endregion
        try {
            setupPlayerUI()

            currentSong = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("song", Song::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("song") as? Song
            }
            clickIndexExtra = intent.getIntExtra("clickIndex", -1)
            // #region debug-point song-received
            Logger.d("PlayerActivity", "[player-cover-landscape-bug] Song from intent: $currentSong, clickIndex=$clickIndexExtra")
            // #endregion

            if (currentSong != null) {
                retainedSong = currentSong
                // Update cover directly (no Fragment needed)
                // #region debug-point updateCoverView-call
                Logger.d("PlayerActivity", "[player-cover-landscape-bug] Calling updateCoverView from onCreate, song.pic=${currentSong!!.pic}")
                // #endregion
                updateCoverView(currentSong!!)
                loadLyrics(currentSong!!)
            }

            if (clickIndexExtra >= 0) {
                pendingPlaylist = SongListHolder.songs
            }

            val serviceIntent = Intent(this, MusicService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            startService(serviceIntent)
        } catch (e: Exception) {
            // #region debug-point onCreate-error
            Logger.e("PlayerActivity", "[player-cover-landscape-bug] onCreate ERROR: ${e.message}", e)
            // #endregion
            e.printStackTrace()
            Toast.makeText(this, "播放器初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isLyricsFocusMode) {
            collapseLyricsFocus()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun setupPlayerUI() {
        setContentView(R.layout.activity_player)
        // #region debug-point setContentView-done
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] setContentView done, orientation=${resources.configuration.orientation}")
        // #endregion

        BackgroundManager.applyToActivity(this)

        initViews()
        // #region debug-point initViews-done
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] initViews done, ivCover initialized=${::ivCover.isInitialized}, coverContainer initialized=${::coverContainer.isInitialized}")
        // #endregion
        setupLyricsFocusAdapter()
        setupListeners()
        setupSwipeGestures()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // #region debug-point config-changed
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] onConfigurationChanged, newOrientation=${newConfig.orientation}, retainedSong=$retainedSong")
        // #endregion

        // 保存当前歌词模式状态，切换布局后恢复
        val wasLyricsFocusMode = isLyricsFocusMode
        if (isLyricsFocusMode) {
            collapseLyricsFocus()
        }

        // 重新加载对应方向的布局
        setupPlayerUI()

        // 恢复界面状态
        retainedSong?.let { song ->
            // #region debug-point updateCoverView-config
            Logger.d("PlayerActivity", "[player-cover-landscape-bug] Calling updateCoverView from onConfigurationChanged, song.pic=${song.pic}")
            // #endregion
            updateCoverView(song)
            // 优先使用已加载的歌词恢复界面，避免异步加载导致歌词模式恢复失败
            if (lyricsList.isNotEmpty()) {
                lyricsFocusAdapter.setLyrics(lyricsList)
                tvNoLyricsPreview?.visibility = View.GONE
                rvLyricsPreview?.visibility = View.VISIBLE
            } else {
                loadLyrics(song)
            }
        }
        updatePlayButtonState(retainedIsPlaying)
        musicService?.let { service ->
            updatePlayModeIcon(service.getPlayMode())
            updateCoverAnimation(service.isPlaying())
        }
        retainedSong?.let { song ->
            lifecycleScope.launch {
                val isFav = repository.isFavorite(song.id).first()
                updateFavoriteIcon(isFav)
            }
        }

        // 如果之前在歌词模式且歌词已加载，恢复歌词界面
        if (wasLyricsFocusMode && lyricsList.isNotEmpty()) {
            expandLyricsFocus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationAnimator?.cancel()
        handler.removeCallbacks(updateProgressRunnable)
        cancelSleepTimer()
        musicService?.removePlaybackListener(playbackListener)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundManager.applyToActivity(this)
        handler.post(updateProgressRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun initViews() {
        // #region debug-point initViews-start
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] initViews START")
        // #endregion
        
        btnClose = findViewById(R.id.btnClose)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnLyrics = findViewById(R.id.btnLyrics)
        btnMore = findViewById(R.id.btnMore)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        // Try to find SeekBar (landscape) or Slider (portrait)
        val seekBarView = findViewById<android.view.View>(R.id.seekBar)
        if (seekBarView is android.widget.SeekBar) {
            seekBar = seekBarView
        } else if (seekBarView is Slider) {
            slider = seekBarView
        }
        btnPlayMode = findViewById(R.id.btnPlayMode)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPlaylist = findViewById(R.id.btnPlaylist)
        cardContainer = findViewById(R.id.cardContainer)
        
        // Cover views
        coverContainer = findViewById<View>(R.id.coverContainer)
        coverCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.coverCard)
        ivCover = findViewById<ImageView>(R.id.ivCover)
        coverPlayOverlay = findViewById(R.id.coverPlayOverlay)
        btnCoverPlay = findViewById(R.id.btnCoverPlay)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        // Background blur (optional, may not exist in portrait)
        bgBlurImage = findViewById<ImageView>(R.id.bgBlurImage)

        // #region debug-point initViews-cover
        val ivCoverParent = ivCover.parent
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] initViews cover views: coverContainer=$coverContainer, coverCard=$coverCard, ivCover=$ivCover, ivCover.id=${ivCover.id}, ivCover.parent=$ivCoverParent")
        // #endregion
        
        // Lyrics preview views (landscape mode)
        lyricsPreviewContainer = findViewById(R.id.lyricsPreviewContainer)
        rvLyricsPreview = findViewById(R.id.rvLyricsPreview)
        tvNoLyricsPreview = findViewById(R.id.tvNoLyricsPreview)

        // Lyrics overlay views (fullscreen)
        lyricsFocusOverlay = findViewById(R.id.lyricsFocusOverlay)
        btnCloseLyrics = findViewById(R.id.btnCloseLyrics)
        rvLyricsFocus = findViewById(R.id.rvLyricsFocus)
        tvNoLyricsFocus = findViewById(R.id.tvNoLyricsFocus)

        // #region debug-point initViews-done
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] initViews DONE, all views initialized")
        // #endregion

        // Setup cover rotation animation
        setupCoverAnimation()

        // 封面长按显示播放按钮
        coverContainer.setOnLongClickListener {
            showCoverPlayOverlay()
            true
        }

        btnCoverPlay.setOnClickListener {
            togglePlayPause()
            hideCoverPlayOverlay()
        }
    }

    private fun setupCoverAnimation() {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 16000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                coverContainer.rotation = value
            }
        }
    }

    private fun showCoverPlayOverlay() {
        coverPlayOverlay.alpha = 0f
        coverPlayOverlay.visibility = View.VISIBLE

        val fadeIn = ObjectAnimator.ofFloat(coverPlayOverlay, "alpha", 0f, 1f)
        fadeIn.duration = 200
        fadeIn.interpolator = DecelerateInterpolator()
        fadeIn.start()

        handler.postDelayed({
            hideCoverPlayOverlay()
        }, 1500)
    }

    private fun hideCoverPlayOverlay() {
        val fadeOut = ObjectAnimator.ofFloat(coverPlayOverlay, "alpha", 1f, 0f)
        fadeOut.duration = 200
        fadeOut.interpolator = AccelerateDecelerateInterpolator()
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                coverPlayOverlay.visibility = View.GONE
            }
        })
        fadeOut.start()
    }

    private fun updateCoverAnimation(playing: Boolean) {
        isPlaying = playing
        if (playing) {
            if (rotationAnimator?.isRunning == false) {
                rotationAnimator?.start()
            } else if (rotationAnimator?.isPaused == true) {
                rotationAnimator?.resume()
            }
        } else {
            rotationAnimator?.pause()
        }
        btnCoverPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun togglePlayPause() {
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_PAUSE
        }
        startService(intent)
    }

    private fun updateCoverView(song: Song) {
        tvTitle.text = song.name
        tvArtist.text = song.artist

        // Load cover image - use getCoverUrl() which returns pic field if it's HTTP URL
        val coverUrl = song.getCoverUrl()
        
        Logger.d("PlayerActivity", "updateCoverView: song=${song.name}, pic=${song.pic}, coverUrl=$coverUrl")

        // Clear previous image first
        Glide.with(this).clear(ivCover)
        ivCover.setImageDrawable(null)
        
        // Clear background blur
        bgBlurImage?.let {
            Glide.with(this).clear(it)
            it.setImageDrawable(null)
        }

        if (coverUrl.isBlank()) {
            Logger.w("PlayerActivity", "Cover URL is blank, using placeholder")
            ivCover.setImageResource(R.drawable.ic_music_note)
            return
        }

        try {
            // Load main cover
            Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .centerCrop()
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Logger.e("PlayerActivity", "Glide load failed: model=$model, error=${e?.message}")
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        Logger.d("PlayerActivity", "Glide load success: model=$model, resource=$resource")
                        return false
                    }
                })
                .into(ivCover)
            
            // Load background blur (for landscape mode)
            bgBlurImage?.let { bgImage ->
                Glide.with(this)
                    .load(coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .centerCrop()
                    .transform(jp.wasabeef.glide.transformations.BlurTransformation(25, 3))
                    .into(bgImage)
            }
        } catch (e: Exception) {
            Logger.e("PlayerActivity", "Error loading cover: ${e.message}", e)
            ivCover.setImageResource(R.drawable.ic_music_note)
        }
    }

    private fun setupLyricsFocusAdapter() {
        lyricsFocusAdapter = LyricsFocusAdapter()
        rvLyricsFocus.adapter = lyricsFocusAdapter
        rvLyricsFocus.layoutManager = LinearLayoutManager(this)

        // Setup preview lyrics adapter for landscape and portrait mode
        rvLyricsPreview?.let { previewRv ->
            previewRv.adapter = lyricsFocusAdapter
            previewRv.layoutManager = LinearLayoutManager(this)
        }
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            if (isLyricsFocusMode) {
                collapseLyricsFocus()
            } else {
                finish()
            }
        }

        btnFavorite.setOnClickListener {
            currentSong?.let { song ->
                lifecycleScope.launch {
                    val isFav = repository.isFavorite(song.id).first()
                    if (isFav) {
                        repository.removeFavorite(song.id)
                    } else {
                        repository.addFavorite(song)
                    }
                    updateFavoriteIcon(!isFav)
                }
            }
        }

        btnMore?.setOnClickListener {
            showMoreDialog()
        }

        btnPlayPause.setOnClickListener {
            animatePlayButton(musicService?.isPlaying() != true)
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            startService(intent)
        }

        btnNext.setOnClickListener {
            animateControlButton(btnNext)
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            startService(intent)
        }

        btnPrevious.setOnClickListener {
            animateControlButton(btnPrevious)
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            startService(intent)
        }

        btnPlayMode.setOnClickListener {
            musicService?.let { service ->
                val newMode = when (service.getPlayMode()) {
                    MusicService.PlayMode.SEQUENCE -> MusicService.PlayMode.RANDOM
                    MusicService.PlayMode.RANDOM -> MusicService.PlayMode.SINGLE
                    MusicService.PlayMode.SINGLE -> MusicService.PlayMode.LOOP
                    MusicService.PlayMode.LOOP -> MusicService.PlayMode.SEQUENCE
                }
                service.setPlayMode(newMode)
                updatePlayModeIcon(newMode)
            }
        }

        btnPlaylist.setOnClickListener {
            showPlaylistPage()
        }

        btnCloseLyrics.setOnClickListener {
            Logger.d("PlayerActivity", "btnCloseLyrics clicked")
            collapseLyricsFocus()
        }

        // 歌词界面通过左右滑动手势进入/退出
        // 保留关闭按钮作为辅助退出方式

        // Setup progress bar listeners
        setupProgressBarListeners()
    }

    private fun setupSwipeGestures() {
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val swipeThreshold = 100
            private val swipeVelocityThreshold = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                // Debounce: ignore rapid repeated swipes
                val now = System.currentTimeMillis()
                if (now - lastSwipeTime < swipeCooldownMs) return false
                // Prevent swipe during transition animation
                if (isLyricsTransitioning) return false
                val diffX: Float = e2.x - e1.x
                val diffY: Float = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (kotlin.math.abs(diffX) > swipeThreshold && kotlin.math.abs(velocityX) > swipeVelocityThreshold) {
                        lastSwipeTime = now
                        if (diffX < 0) {
                            // 从右向左滑：进入歌词页
                            if (!isLyricsFocusMode) {
                                expandLyricsFocus()
                            }
                        } else {
                            // 从左向右滑：退出歌词页
                            if (isLyricsFocusMode) {
                                collapseLyricsFocus()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun expandLyricsFocus() {
        Logger.d("PlayerActivity", "expandLyricsFocus: lyricsList.isEmpty=${lyricsList.isEmpty()}, isLyricsFocusMode=$isLyricsFocusMode")
        if (lyricsList.isEmpty() || isLyricsFocusMode || isLyricsTransitioning) return

        isLyricsTransitioning = true
        isLyricsFocusMode = true
        Logger.d("PlayerActivity", "expandLyricsFocus: entering lyrics mode")
        val duration = 180L

        lyricsFocusAdapter.setLyrics(lyricsList)
        lyricsFocusAdapter.setCurrentIndex(currentLyricIndex)

        lyricsFocusOverlay.visibility = View.VISIBLE
        lyricsFocusOverlay.alpha = 0f
        lyricsFocusOverlay.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isLyricsTransitioning = false
                }
            })
            .start()

        btnCloseLyrics.visibility = View.VISIBLE
        btnCloseLyrics.alpha = 0f
        btnCloseLyrics.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()


    }

    private fun collapseLyricsFocus() {
        Logger.d("PlayerActivity", "collapseLyricsFocus: isLyricsFocusMode=$isLyricsFocusMode")
        if (!isLyricsFocusMode || isLyricsTransitioning) return
        isLyricsTransitioning = true
        isLyricsFocusMode = false
        Logger.d("PlayerActivity", "collapseLyricsFocus: exiting lyrics mode")
        val duration = 150L

        btnCloseLyrics.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    btnCloseLyrics.visibility = View.GONE
                }
            })
            .start()

        lyricsFocusOverlay.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lyricsFocusOverlay.visibility = View.GONE
                    isLyricsTransitioning = false
                }
            })
            .start()
    }

    private fun updateLyricsFocusPosition(position: Int) {
        if (lyricsList.isEmpty()) return

        val newIndex = findLyricIndexByPosition(position)
        if (newIndex != currentLyricIndex) {
            currentLyricIndex = newIndex
            lyricsFocusAdapter.setCurrentIndex(newIndex)

            // Scroll fullscreen lyrics if in focus mode, centered
            if (isLyricsFocusMode) {
                smoothScrollToCenter(rvLyricsFocus, newIndex)
            }

            // Also scroll preview lyrics (landscape mode), centered
            rvLyricsPreview?.let { previewRv ->
                smoothScrollToCenter(previewRv, newIndex)
            }
        }
    }

    private fun smoothScrollToCenter(recyclerView: RecyclerView, position: Int) {
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                // Center the item in the RecyclerView
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }
        }
        smoothScroller.targetPosition = position
        recyclerView.layoutManager?.startSmoothScroll(smoothScroller)
    }

    private fun findLyricIndexByPosition(position: Int): Int {
        if (lyricsList.isEmpty()) return -1
        // Binary search: find the first index where time > position,
        // then return the previous index (the current lyric line)
        var left = 0
        var right = lyricsList.size
        while (left < right) {
            val mid = (left + right) / 2
            if (lyricsList[mid].time <= position) {
                left = mid + 1
            } else {
                right = mid
            }
        }
        return maxOf(0, left - 1)
    }

    private fun animateControlButton(button: View) {
        button.alpha = 0.5f
        button.animate()
            .alpha(1f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animatePlayButton(isPlaying: Boolean) {
        val scaleFrom = if (isPlaying) 1f else 0.8f
        val scaleTo = if (isPlaying) 1.1f else 1f

        val scaleX = ObjectAnimator.ofFloat(btnPlayPause, "scaleX", scaleFrom, scaleTo)
        val scaleY = ObjectAnimator.ofFloat(btnPlayPause, "scaleY", scaleFrom, scaleTo)

        scaleX.duration = 200
        scaleY.duration = 200
        scaleX.interpolator = OvershootInterpolator()
        scaleY.interpolator = OvershootInterpolator()

        scaleX.start()
        scaleY.start()
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updatePlayModeIcon(mode: MusicService.PlayMode) {
        btnPlayMode.setImageResource(
            when (mode) {
                MusicService.PlayMode.SEQUENCE -> R.drawable.ic_repeat
                MusicService.PlayMode.RANDOM -> R.drawable.ic_shuffle
                MusicService.PlayMode.SINGLE -> R.drawable.ic_repeat_one
                MusicService.PlayMode.LOOP -> R.drawable.ic_repeat
            }
        )
        val color = if (mode == MusicService.PlayMode.RANDOM || mode == MusicService.PlayMode.SINGLE) {
            getColor(R.color.primary)
        } else {
            getColor(R.color.text_secondary)
        }
        btnPlayMode.setColorFilter(color)
    }

    private fun updateFavoriteIcon(isFav: Boolean) {
        btnFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    private fun loadLyrics(song: Song) {
        lifecycleScope.launch {
            try {
                // Check cache first
                val cached = LyricsCache.get(song.id)
                if (cached != null) {
                    lyricsList = cached
                    applyLyricsToUI(cached)
                    return@launch
                }

                // Load from network with retry
                val lrcText = loadLyricsWithRetry(song.id)
                if (!lrcText.isNullOrBlank()) {
                    val parsed = LyricParser.parse(lrcText)
                    if (parsed.isNotEmpty()) {
                        lyricsList = parsed
                        LyricsCache.put(song.id, parsed)
                        applyLyricsToUI(parsed)
                        return@launch
                    }
                }
                lyricsList = emptyList()
                showNoLyrics()
            } catch (e: Exception) {
                Logger.e("PlayerActivity", "Failed to load lyrics for ${song.id}", e)
                lyricsList = emptyList()
                showNoLyrics()
            }
        }
    }

    private suspend fun loadLyricsWithRetry(songId: String, maxRetries: Int = 3): String? {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val result = withContext(Dispatchers.IO) { repository.getLyrics(songId) }
                if (!result.isNullOrBlank()) return result
                Logger.d("PlayerActivity", "loadLyrics attempt $attempt: empty result for $songId")
            } catch (e: Exception) {
                lastException = e
                Logger.w("PlayerActivity", "loadLyrics attempt $attempt failed for $songId: ${e.message}")
                if (attempt < maxRetries) {
                    delay(1000L * attempt) // Exponential backoff: 1s, 2s, 3s
                }
            }
        }
        if (lastException != null) {
            Logger.e("PlayerActivity", "loadLyrics all $maxRetries retries failed for $songId", lastException)
        }
        return null
    }

    private fun applyLyricsToUI(parsed: List<LyricLine>) {
        lyricsFocusAdapter.setLyrics(parsed)
        tvNoLyricsFocus.visibility = View.GONE
        rvLyricsFocus.visibility = View.VISIBLE
        tvNoLyricsPreview?.visibility = View.GONE
        rvLyricsPreview?.visibility = View.VISIBLE
        if (isLyricsFocusMode) {
            lyricsFocusAdapter.notifyDataSetChanged()
        }
    }

    private fun showNoLyrics() {
        tvNoLyricsFocus.visibility = View.VISIBLE
        tvNoLyricsFocus.text = "歌词加载失败"
        rvLyricsFocus.visibility = View.GONE
        tvNoLyricsPreview?.visibility = View.VISIBLE
        tvNoLyricsPreview?.text = "歌词加载失败"
        rvLyricsPreview?.visibility = View.GONE
    }

    private fun initPlayer() {
        // #region debug-point initPlayer-start
        Logger.d("PlayerActivity", "[player-cover-landscape-bug] initPlayer START, musicService=$musicService, currentSong=$currentSong, retainedSong=$retainedSong")
        // #endregion
        musicService?.let { service ->
            val autoPlay = intent.getBooleanExtra("autoPlay", false)

            if (pendingPlaylist != null && clickIndexExtra >= 0) {
                service.setPlaylist(pendingPlaylist!!, clickIndexExtra)
                // 如果设置了自动播放，则开始播放
                if (autoPlay && !service.isPlaying()) {
                    service.playCurrent()
                }
            } else if (currentSong != null) {
                if (service.getCurrentSong()?.id != currentSong?.id) {
                    service.playSong(currentSong!!)
                } else if (autoPlay && !service.isPlaying()) {
                    // 同一首歌但要求自动播放且当前未播放
                    service.playCurrent()
                }
            }

            val mode = service.getPlayMode()
            updatePlayModeIcon(mode)
            updatePlayButtonState(service.isPlaying())

            // Use currentSong from intent if available, otherwise use service's current song
            val songToDisplay = currentSong ?: service.getCurrentSong()
            Logger.d("PlayerActivity", "initPlayer: currentSong=$currentSong, serviceSong=${service.getCurrentSong()}, songToDisplay=$songToDisplay")
            if (songToDisplay != null) {
                updateCoverView(songToDisplay)
                loadLyrics(songToDisplay)
            }

            updateCoverAnimation(service.isPlaying())
            currentSong?.let {
                lifecycleScope.launch {
                    val isFav = repository.isFavorite(it.id).first()
                    updateFavoriteIcon(isFav)
                }
            }
        }
    }

    private fun showMoreDialog() {
        Logger.d("PlayerActivity", "showMoreDialog: START")
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_player_more, null)
        dialog.setContentView(view)
        Logger.d("PlayerActivity", "showMoreDialog: dialog content set")

        // --- 定时关闭相关控件 ---
        val tvCurrentTimer = view.findViewById<TextView>(R.id.tvCurrentTimer)
        val btn15Min = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn15Min)
        val btn30Min = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn30Min)
        val btn60Min = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn60Min)
        val btnEndOfSong = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEndOfSong)
        val btnAfter1Song = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAfter1Song)
        val btnAfter2Songs = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAfter2Songs)
        val btnCancelTimer = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelTimer)
        val btnClose = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClose)

        // Update current timer status
        if (isSleepTimerActive) {
            val statusText = when (sleepTimerMode) {
                SleepTimerMode.FIXED_TIME -> {
                    val remainingMinutes = ((sleepTimerEndTime - System.currentTimeMillis()) / 60000).toInt()
                    if (remainingMinutes > 0) "定时关闭：剩余 ${remainingMinutes} 分钟" else null
                }
                SleepTimerMode.END_OF_SONG -> "定时关闭：播放完当前歌曲"
                SleepTimerMode.AFTER_SONGS -> "定时关闭：再播${remainingSongsCount}首后关闭"
                else -> null
            }
            if (statusText != null) {
                tvCurrentTimer.text = statusText
                tvCurrentTimer.visibility = View.VISIBLE
                btnCancelTimer.visibility = View.VISIBLE
            } else {
                cancelSleepTimer()
                tvCurrentTimer.visibility = View.GONE
                btnCancelTimer.visibility = View.GONE
            }
        } else {
            tvCurrentTimer.visibility = View.GONE
            btnCancelTimer.visibility = View.GONE
        }

        // 定时关闭 - 按歌曲剩余时长
        btnEndOfSong.setOnClickListener {
            Logger.d("PlayerActivity", "btnEndOfSong clicked")
            setSleepTimerEndOfSong()
            dialog.dismiss()
            Toast.makeText(this, "将在当前歌曲播放完毕后关闭", Toast.LENGTH_SHORT).show()
        }

        btnAfter1Song.setOnClickListener {
            Logger.d("PlayerActivity", "btnAfter1Song clicked")
            setSleepTimerAfterSongs(1)
            dialog.dismiss()
            Toast.makeText(this, "将在播放完下1首歌曲后关闭", Toast.LENGTH_SHORT).show()
        }

        btnAfter2Songs.setOnClickListener {
            Logger.d("PlayerActivity", "btnAfter2Songs clicked")
            setSleepTimerAfterSongs(2)
            dialog.dismiss()
            Toast.makeText(this, "将在播放完下2首歌曲后关闭", Toast.LENGTH_SHORT).show()
        }

        // 定时关闭 - 按固定时长
        btn15Min.setOnClickListener {
            Logger.d("PlayerActivity", "btn15Min clicked")
            setSleepTimer(15)
            dialog.dismiss()
            Toast.makeText(this, "已设置 15 分钟后关闭", Toast.LENGTH_SHORT).show()
        }

        btn30Min.setOnClickListener {
            Logger.d("PlayerActivity", "btn30Min clicked")
            setSleepTimer(30)
            dialog.dismiss()
            Toast.makeText(this, "已设置 30 分钟后关闭", Toast.LENGTH_SHORT).show()
        }

        btn60Min.setOnClickListener {
            Logger.d("PlayerActivity", "btn60Min clicked")
            setSleepTimer(60)
            dialog.dismiss()
            Toast.makeText(this, "已设置 60 分钟后关闭", Toast.LENGTH_SHORT).show()
        }

        // 取消定时
        btnCancelTimer.setOnClickListener {
            cancelSleepTimer()
            dialog.dismiss()
            Toast.makeText(this, "已取消定时关闭", Toast.LENGTH_SHORT).show()
        }

        // 关闭对话框
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // --- 倍速播放相关控件 ---
        val chipGroupSpeed = view.findViewById<ChipGroup>(R.id.chipGroupSpeed)
        // 根据当前速度选中对应的 Chip
        val currentSpeed = musicService?.getSpeed() ?: 1.0f
        val speedChipId = when {
            currentSpeed <= 0.5f -> R.id.chipSpeed0_5
            currentSpeed <= 0.75f -> R.id.chipSpeed0_75
            currentSpeed <= 1.0f -> R.id.chipSpeed1_0
            currentSpeed <= 1.25f -> R.id.chipSpeed1_25
            currentSpeed <= 1.5f -> R.id.chipSpeed1_5
            else -> R.id.chipSpeed2_0
        }
        chipGroupSpeed.check(speedChipId)

        chipGroupSpeed.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds[0]
            val speed = when (checkedId) {
                R.id.chipSpeed0_5 -> 0.5f
                R.id.chipSpeed0_75 -> 0.75f
                R.id.chipSpeed1_0 -> 1.0f
                R.id.chipSpeed1_25 -> 1.25f
                R.id.chipSpeed1_5 -> 1.5f
                R.id.chipSpeed2_0 -> 2.0f
                else -> 1.0f
            }
            musicService?.setSpeed(speed)
            val chip = view.findViewById<Chip>(checkedId)
            Toast.makeText(this, "倍速: ${chip.text}", Toast.LENGTH_SHORT).show()
        }

        // --- 均衡器入口 ---
        val btnEqualizer = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEqualizer)
        btnEqualizer.setOnClickListener {
            val audioSessionId = musicService?.getAudioSessionId() ?: 0
            EqualizerActivity.start(this, audioSessionId)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setSleepTimer(minutes: Int) {
        Logger.d("PlayerActivity", "setSleepTimer: minutes=$minutes")
        cancelSleepTimer()

        sleepTimerMode = SleepTimerMode.FIXED_TIME
        sleepTimerHandler = Handler(Looper.getMainLooper())
        sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        isSleepTimerActive = true

        sleepTimerRunnable = Runnable {
            Logger.d("PlayerActivity", "sleepTimerRunnable: timer triggered!")
            stopMusicAndClose()
        }

        sleepTimerHandler?.postDelayed(sleepTimerRunnable!!, minutes * 60 * 1000L)
        Logger.d("PlayerActivity", "setSleepTimer: timer scheduled for $minutes minutes")
    }

    private fun setSleepTimerEndOfSong() {
        Logger.d("PlayerActivity", "setSleepTimerEndOfSong")
        cancelSleepTimer()
        sleepTimerMode = SleepTimerMode.END_OF_SONG
        isSleepTimerActive = true
        remainingSongsCount = 0
    }

    private fun setSleepTimerAfterSongs(songCount: Int) {
        Logger.d("PlayerActivity", "setSleepTimerAfterSongs: songCount=$songCount")
        cancelSleepTimer()
        sleepTimerMode = SleepTimerMode.AFTER_SONGS
        isSleepTimerActive = true
        remainingSongsCount = songCount
    }

    private fun cancelSleepTimer() {
        Logger.d("PlayerActivity", "cancelSleepTimer")
        sleepTimerRunnable?.let { sleepTimerHandler?.removeCallbacks(it) }
        sleepTimerHandler = null
        sleepTimerRunnable = null
        isSleepTimerActive = false
        sleepTimerEndTime = 0
        sleepTimerMode = SleepTimerMode.OFF
        remainingSongsCount = 0
    }

    private fun stopMusicAndClose() {
        Logger.d("PlayerActivity", "stopMusicAndClose: stopping music and closing activity")
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        startService(intent)
        finish()
    }

    fun onSongCompleted() {
        Logger.d("PlayerActivity", "onSongCompleted: sleepTimerMode=$sleepTimerMode, remainingSongsCount=$remainingSongsCount")
        if (!isSleepTimerActive) return

        when (sleepTimerMode) {
            SleepTimerMode.END_OF_SONG -> {
                Logger.d("PlayerActivity", "onSongCompleted: END_OF_SONG mode, stopping")
                stopMusicAndClose()
            }
            SleepTimerMode.AFTER_SONGS -> {
                remainingSongsCount--
                Logger.d("PlayerActivity", "onSongCompleted: AFTER_SONGS mode, remaining=$remainingSongsCount")
                if (remainingSongsCount <= 0) {
                    stopMusicAndClose()
                }
            }
            else -> {
                // FIXED_TIME mode, do nothing here (handled by Handler)
            }
        }
    }

    private fun showPlaylistPage() {
        PlaylistActivity.start(this)
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun setProgressBarValue(value: Int) {
        seekBar?.progress = value
        slider?.value = value.toFloat()
    }

    private fun setProgressBarMax(max: Int) {
        seekBar?.max = max
        slider?.valueTo = max.toFloat()
    }

    private fun setupProgressBarListeners() {
        seekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let { musicService?.seekTo(it.progress) }
            }
        })

        slider?.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                tvCurrentTime.text = formatTime(value.toInt())
            }
        })

        slider?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                musicService?.seekTo(slider.value.toInt())
            }
        })
    }
}

class LyricsFocusAdapter : RecyclerView.Adapter<LyricsFocusAdapter.LyricViewHolder>() {
    private var lyrics = listOf<LyricLine>()
    private var currentIndex = -1

    companion object {
        private const val ACTIVE_SIZE = 20f
        private const val NEXT_SIZE = 15f
        private const val INACTIVE_SIZE = 13f
        private const val ACTIVE_ALPHA = 0.85f
        private const val INACTIVE_ALPHA = 0.35f
    }

    fun setLyrics(newLyrics: List<LyricLine>) {
        lyrics = newLyrics
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        val oldIndex = currentIndex
        currentIndex = index
        if (oldIndex >= 0 && oldIndex < lyrics.size) notifyItemChanged(oldIndex, "index_update")
        if (index >= 0 && index < lyrics.size) notifyItemChanged(index, "index_update")
        if (oldIndex >= 0 && oldIndex < lyrics.size) {
            if (oldIndex + 1 < lyrics.size) notifyItemChanged(oldIndex + 1, "index_update")
            if (oldIndex - 1 >= 0) notifyItemChanged(oldIndex - 1, "index_update")
        }
        if (index >= 0 && index < lyrics.size) {
            if (index + 1 < lyrics.size) notifyItemChanged(index + 1, "index_update")
            if (index - 1 >= 0) notifyItemChanged(index - 1, "index_update")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_focus, parent, false)
        return LyricViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            holder.bind(lyrics[position], position, currentIndex)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        holder.bind(lyrics[position], position, currentIndex)
    }

    override fun getItemCount() = lyrics.size

    class LyricViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLyricLine: TextView = itemView.findViewById(R.id.tvLyricLine)

        fun bind(lyric: LyricLine, position: Int, currentIndex: Int) {
            tvLyricLine.text = lyric.text

            when {
                position == currentIndex -> {
                    tvLyricLine.alpha = ACTIVE_ALPHA
                    tvLyricLine.textSize = ACTIVE_SIZE
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_primary))
                }
                position == currentIndex + 1 -> {
                    tvLyricLine.alpha = INACTIVE_ALPHA + 0.1f
                    tvLyricLine.textSize = NEXT_SIZE
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_secondary))
                }
                else -> {
                    tvLyricLine.alpha = INACTIVE_ALPHA
                    tvLyricLine.textSize = INACTIVE_SIZE
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_tertiary))
                }
            }
        }
    }
}
