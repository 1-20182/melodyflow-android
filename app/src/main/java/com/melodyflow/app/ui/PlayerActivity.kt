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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
import com.melodyflow.app.util.LyricParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageButton
    private lateinit var btnFavorite: ImageButton
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
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] onServiceConnected START")
            // #endregion
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
            musicService?.addPlaybackListener(playbackListener)
            // #region debug-point before-initPlayer
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] Calling initPlayer from onServiceConnected")
            // #endregion
            initPlayer()
            // #region debug-point after-initPlayer
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] initPlayer completed")
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
            android.util.Log.d("PlayerActivity", "onSongChanged: song=${song?.name}")
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
                    android.util.Log.d("PlayerActivity", "Progress update skipped: player not ready")
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // #region debug-point onCreate-start
        android.util.Log.d("DEBUG", "[player-cover-landscape-bug] onCreate START, savedInstanceState=$savedInstanceState, retainedSong=$retainedSong")
        // #endregion
        try {
            setContentView(R.layout.activity_player)
            // #region debug-point setContentView-done
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] setContentView done, orientation=${resources.configuration.orientation}")
            // #endregion

            BackgroundManager.applyToActivity(this)

            initViews()
            // #region debug-point initViews-done
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] initViews done, ivCover initialized=${::ivCover.isInitialized}, coverContainer initialized=${::coverContainer.isInitialized}")
            // #endregion
            setupLyricsFocusAdapter()
            setupListeners()

            currentSong = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("song", Song::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("song") as? Song
            }
            clickIndexExtra = intent.getIntExtra("clickIndex", -1)
            // #region debug-point song-received
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] Song from intent: $currentSong, clickIndex=$clickIndexExtra")
            // #endregion

            if (currentSong != null) {
                retainedSong = currentSong
                // Update cover directly (no Fragment needed)
                // #region debug-point updateCoverView-call
                android.util.Log.d("DEBUG", "[player-cover-landscape-bug] Calling updateCoverView from onCreate, song.pic=${currentSong!!.pic}")
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
            android.util.Log.e("DEBUG", "[player-cover-landscape-bug] onCreate ERROR: ${e.message}", e)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // #region debug-point config-changed
        android.util.Log.d("DEBUG", "[player-cover-landscape-bug] onConfigurationChanged, newOrientation=${newConfig.orientation}, retainedSong=$retainedSong")
        // #endregion
        if (isLyricsFocusMode) {
            collapseLyricsFocus()
        }
        retainedSong?.let { song ->
            // #region debug-point updateCoverView-config
            android.util.Log.d("DEBUG", "[player-cover-landscape-bug] Calling updateCoverView from onConfigurationChanged, song.pic=${song.pic}")
            // #endregion
            updateCoverView(song)
        }
        updatePlayButtonState(retainedIsPlaying)
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
        android.util.Log.d("DEBUG", "[player-cover-landscape-bug] initViews START")
        // #endregion
        
        btnClose = findViewById(R.id.btnClose)
        btnFavorite = findViewById(R.id.btnFavorite)
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
        android.util.Log.d("DEBUG", "[player-cover-landscape-bug] initViews cover views: coverContainer=$coverContainer, coverCard=$coverCard, ivCover=$ivCover, ivCover.id=${ivCover.id}, ivCover.parent=$ivCoverParent")
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
        android.util.Log.d("DEBUG", "[player-cover-landscape-bug] initViews DONE, all views initialized")
        // #endregion

        // Setup cover rotation animation
        setupCoverAnimation()
        
        // Setup cover click listener - 点击封面进入歌词界面
        coverContainer.setOnClickListener {
            android.util.Log.d("PlayerActivity", "coverContainer clicked")
            expandLyricsFocus()
        }

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
        
        android.util.Log.d("PlayerActivity", "updateCoverView: song=${song.name}, pic=${song.pic}, coverUrl=$coverUrl")

        // Clear previous image first
        Glide.with(this).clear(ivCover)
        ivCover.setImageDrawable(null)
        
        // Clear background blur
        bgBlurImage?.let {
            Glide.with(this).clear(it)
            it.setImageDrawable(null)
        }

        if (coverUrl.isBlank()) {
            android.util.Log.w("PlayerActivity", "Cover URL is blank, using placeholder")
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
                        android.util.Log.e("PlayerActivity", "Glide load failed: model=$model, error=${e?.message}")
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        android.util.Log.d("PlayerActivity", "Glide load success: model=$model, resource=$resource")
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
            android.util.Log.e("PlayerActivity", "Error loading cover: ${e.message}", e)
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
            showPlaylistDialog()
        }

        btnCloseLyrics.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btnCloseLyrics clicked")
            collapseLyricsFocus()
        }

        // 移除了 lyricsFocusOverlay 的点击事件，避免误触关闭
        // 现在只能通过关闭按钮关闭歌词界面

        // 歌词预览区域点击事件已移除，改为点击封面进入歌词界面

        // Setup progress bar listeners
        setupProgressBarListeners()
    }

    private fun expandLyricsFocus() {
        android.util.Log.d("PlayerActivity", "expandLyricsFocus: lyricsList.isEmpty=${lyricsList.isEmpty()}, isLyricsFocusMode=$isLyricsFocusMode")
        if (lyricsList.isEmpty() || isLyricsFocusMode) return

        isLyricsFocusMode = true
        android.util.Log.d("PlayerActivity", "expandLyricsFocus: entering lyrics mode")
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val duration = if (isLandscape) 300L else 250L

        lyricsFocusAdapter.setLyrics(lyricsList)
        lyricsFocusAdapter.setCurrentIndex(currentLyricIndex)

        lyricsFocusOverlay.visibility = View.VISIBLE
        lyricsFocusOverlay.alpha = 0f
        lyricsFocusOverlay.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()

        btnCloseLyrics.visibility = View.VISIBLE
        btnCloseLyrics.alpha = 0f
        btnCloseLyrics.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()

        cardContainer?.animate()
            ?.scaleX(1.08f)
            ?.scaleY(1.08f)
            ?.translationZ(16f)
            ?.setDuration(duration)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()


    }

    private fun collapseLyricsFocus() {
        android.util.Log.d("PlayerActivity", "collapseLyricsFocus: isLyricsFocusMode=$isLyricsFocusMode")
        if (!isLyricsFocusMode) return
        isLyricsFocusMode = false
        android.util.Log.d("PlayerActivity", "collapseLyricsFocus: exiting lyrics mode")
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val duration = if (isLandscape) 250L else 200L

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
                }
            })
            .start()

        cardContainer?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.translationZ(0f)
            ?.setDuration(duration)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()
    }

    private fun updateLyricsFocusPosition(position: Int) {
        if (lyricsList.isEmpty()) return

        val newIndex = findLyricIndexByPosition(position)
        if (newIndex != currentLyricIndex) {
            currentLyricIndex = newIndex
            lyricsFocusAdapter.setCurrentIndex(newIndex)

            // Scroll fullscreen lyrics if in focus mode
            if (isLyricsFocusMode) {
                val layoutManager = rvLyricsFocus.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(newIndex, rvLyricsFocus.height / 3)
            }

            // Also scroll preview lyrics (landscape mode)
            rvLyricsPreview?.let { previewRv ->
                val previewLayoutManager = previewRv.layoutManager as? LinearLayoutManager
                previewLayoutManager?.scrollToPositionWithOffset(newIndex, previewRv.height / 3)
            }
        }
    }

    private fun findLyricIndexByPosition(position: Int): Int {
        for (i in lyricsList.indices) {
            if (lyricsList[i].time > position) {
                return maxOf(0, i - 1)
            }
        }
        return lyricsList.size - 1
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
                val lrcText = withContext(Dispatchers.IO) { repository.getLyrics(song.id) }
                if (!lrcText.isNullOrBlank()) {
                    val parsed = LyricParser.parse(lrcText)
                    if (parsed.isNotEmpty()) {
                        lyricsList = parsed
                        lyricsFocusAdapter.setLyrics(parsed)
                        // Update fullscreen lyrics visibility
                        tvNoLyricsFocus.visibility = View.GONE
                        rvLyricsFocus.visibility = View.VISIBLE
                        // Update preview lyrics visibility (landscape mode)
                        tvNoLyricsPreview?.visibility = View.GONE
                        rvLyricsPreview?.visibility = View.VISIBLE
                        if (isLyricsFocusMode) {
                            lyricsFocusAdapter.notifyDataSetChanged()
                        }
                        return@launch
                    }
                }
                lyricsList = emptyList()
                // Show "no lyrics" in fullscreen
                tvNoLyricsFocus.visibility = View.VISIBLE
                tvNoLyricsFocus.text = "暂无歌词"
                rvLyricsFocus.visibility = View.GONE
                // Show "no lyrics" in preview (landscape mode)
                tvNoLyricsPreview?.visibility = View.VISIBLE
                tvNoLyricsPreview?.text = "暂无歌词"
                rvLyricsPreview?.visibility = View.GONE
            } catch (e: Exception) {
                android.util.Log.e("PlayerActivity", "Failed to load lyrics for ${song.id}", e)
                lyricsList = emptyList()
                // Show error in fullscreen
                tvNoLyricsFocus.visibility = View.VISIBLE
                tvNoLyricsFocus.text = "歌词加载失败"
                rvLyricsFocus.visibility = View.GONE
                // Show error in preview (landscape mode)
                tvNoLyricsPreview?.visibility = View.VISIBLE
                tvNoLyricsPreview?.text = "歌词加载失败"
                rvLyricsPreview?.visibility = View.GONE
            }
        }
    }

    private fun initPlayer() {
        // #region debug-point initPlayer-start
        android.util.Log.d("DEBUG", "[player-cover-landscape-bug] initPlayer START, musicService=$musicService, currentSong=$currentSong, retainedSong=$retainedSong")
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
            android.util.Log.d("PlayerActivity", "initPlayer: currentSong=$currentSong, serviceSong=${service.getCurrentSong()}, songToDisplay=$songToDisplay")
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
        android.util.Log.d("PlayerActivity", "showMoreDialog: START")
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_sleep_timer, null)
        dialog.setContentView(view)
        android.util.Log.d("PlayerActivity", "showMoreDialog: dialog content set")

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

        // 按歌曲剩余时长 - 播放完当前歌曲
        btnEndOfSong.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btnEndOfSong clicked")
            setSleepTimerEndOfSong()
            dialog.dismiss()
            Toast.makeText(this, "将在当前歌曲播放完毕后关闭", Toast.LENGTH_SHORT).show()
        }

        // 按歌曲剩余时长 - 再播1首
        btnAfter1Song.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btnAfter1Song clicked")
            setSleepTimerAfterSongs(1)
            dialog.dismiss()
            Toast.makeText(this, "将在播放完下1首歌曲后关闭", Toast.LENGTH_SHORT).show()
        }

        // 按歌曲剩余时长 - 再播2首
        btnAfter2Songs.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btnAfter2Songs clicked")
            setSleepTimerAfterSongs(2)
            dialog.dismiss()
            Toast.makeText(this, "将在播放完下2首歌曲后关闭", Toast.LENGTH_SHORT).show()
        }

        // 按固定时长
        btn15Min.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btn15Min clicked")
            setSleepTimer(15)
            dialog.dismiss()
            Toast.makeText(this, "已设置 15 分钟后关闭", Toast.LENGTH_SHORT).show()
        }

        btn30Min.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btn30Min clicked")
            setSleepTimer(30)
            dialog.dismiss()
            Toast.makeText(this, "已设置 30 分钟后关闭", Toast.LENGTH_SHORT).show()
        }

        btn60Min.setOnClickListener {
            android.util.Log.d("PlayerActivity", "btn60Min clicked")
            setSleepTimer(60)
            dialog.dismiss()
            Toast.makeText(this, "已设置 60 分钟后关闭", Toast.LENGTH_SHORT).show()
        }

        // Cancel timer
        btnCancelTimer.setOnClickListener {
            cancelSleepTimer()
            dialog.dismiss()
            Toast.makeText(this, "已取消定时关闭", Toast.LENGTH_SHORT).show()
        }

        // Close dialog
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setSleepTimer(minutes: Int) {
        android.util.Log.d("PlayerActivity", "setSleepTimer: minutes=$minutes")
        cancelSleepTimer()

        sleepTimerMode = SleepTimerMode.FIXED_TIME
        sleepTimerHandler = Handler(Looper.getMainLooper())
        sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        isSleepTimerActive = true

        sleepTimerRunnable = Runnable {
            android.util.Log.d("PlayerActivity", "sleepTimerRunnable: timer triggered!")
            stopMusicAndClose()
        }

        sleepTimerHandler?.postDelayed(sleepTimerRunnable!!, minutes * 60 * 1000L)
        android.util.Log.d("PlayerActivity", "setSleepTimer: timer scheduled for $minutes minutes")
    }

    private fun setSleepTimerEndOfSong() {
        android.util.Log.d("PlayerActivity", "setSleepTimerEndOfSong")
        cancelSleepTimer()
        sleepTimerMode = SleepTimerMode.END_OF_SONG
        isSleepTimerActive = true
        remainingSongsCount = 0
    }

    private fun setSleepTimerAfterSongs(songCount: Int) {
        android.util.Log.d("PlayerActivity", "setSleepTimerAfterSongs: songCount=$songCount")
        cancelSleepTimer()
        sleepTimerMode = SleepTimerMode.AFTER_SONGS
        isSleepTimerActive = true
        remainingSongsCount = songCount
    }

    private fun cancelSleepTimer() {
        android.util.Log.d("PlayerActivity", "cancelSleepTimer")
        sleepTimerRunnable?.let { sleepTimerHandler?.removeCallbacks(it) }
        sleepTimerHandler = null
        sleepTimerRunnable = null
        isSleepTimerActive = false
        sleepTimerEndTime = 0
        sleepTimerMode = SleepTimerMode.OFF
        remainingSongsCount = 0
    }

    private fun stopMusicAndClose() {
        android.util.Log.d("PlayerActivity", "stopMusicAndClose: stopping music and closing activity")
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        startService(intent)
        finish()
    }

    fun onSongCompleted() {
        android.util.Log.d("PlayerActivity", "onSongCompleted: sleepTimerMode=$sleepTimerMode, remainingSongsCount=$remainingSongsCount")
        if (!isSleepTimerActive) return

        when (sleepTimerMode) {
            SleepTimerMode.END_OF_SONG -> {
                android.util.Log.d("PlayerActivity", "onSongCompleted: END_OF_SONG mode, stopping")
                stopMusicAndClose()
            }
            SleepTimerMode.AFTER_SONGS -> {
                remainingSongsCount--
                android.util.Log.d("PlayerActivity", "onSongCompleted: AFTER_SONGS mode, remaining=$remainingSongsCount")
                if (remainingSongsCount <= 0) {
                    stopMusicAndClose()
                }
            }
            else -> {
                // FIXED_TIME mode, do nothing here (handled by Handler)
            }
        }
    }

    private fun showPlaylistDialog() {
        val playlist = musicService?.getPlaylist() ?: emptyList()
        if (playlist.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_playlist, null)
        dialog.setContentView(view)

        val rvPlaylist = view.findViewById<RecyclerView>(R.id.rvPlaylist)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        tvTitle.text = "播放列表 (${playlist.size})"

        val currentSongId = musicService?.getCurrentSong()?.id
        val adapter = com.melodyflow.app.adapter.PlaylistAdapter(currentSongId) { song ->
            musicService?.playSong(song)
            dialog.dismiss()
        }
        adapter.submitList(playlist)

        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvPlaylist.adapter = adapter
        rvPlaylist.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

    fun setLyrics(newLyrics: List<LyricLine>) {
        lyrics = newLyrics
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        val oldIndex = currentIndex
        currentIndex = index
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_focus, parent, false)
        return LyricViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        holder.bind(lyrics[position], position == currentIndex, position, currentIndex)
    }

    override fun getItemCount() = lyrics.size

    class LyricViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLyricLine: TextView = itemView.findViewById(R.id.tvLyricLine)

        fun bind(lyric: LyricLine, isCurrent: Boolean, position: Int, currentIndex: Int) {
            tvLyricLine.text = lyric.text

            when {
                position == currentIndex -> {
                    tvLyricLine.alpha = 0.9f
                    tvLyricLine.textSize = 26f
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_primary))
                }
                position == currentIndex + 1 -> {
                    tvLyricLine.alpha = 0.4f
                    tvLyricLine.textSize = 18f
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_secondary))
                }
                position < currentIndex -> {
                    tvLyricLine.alpha = 0.2f
                    tvLyricLine.textSize = 16f
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_tertiary))
                }
                else -> {
                    tvLyricLine.alpha = 0.2f
                    tvLyricLine.textSize = 16f
                    tvLyricLine.setTextColor(itemView.context.getColor(R.color.text_tertiary))
                }
            }
        }
    }
}
