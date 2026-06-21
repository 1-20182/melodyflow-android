package com.melodyflow.app.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.melodyflow.app.BuildConfig
import com.melodyflow.app.R
import com.melodyflow.app.adapter.MainPagerAdapter
import com.melodyflow.app.adapter.PlaylistAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private var bottomNav: BottomNavigationView? = null
    private var navigationRail: NavigationRailView? = null
    private lateinit var miniPlayer: View
    private lateinit var ivMiniCover: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlay: ImageButton
    private lateinit var btnMiniNext: ImageButton
    private lateinit var btnMiniPrevious: ImageButton

    // 迷你播放器进度条相关
    private var seekBarMini: com.google.android.material.slider.Slider? = null
    private var tvMiniCurrentTime: TextView? = null
    private var tvMiniTotalTime: TextView? = null
    private var btnMiniPlaylist: ImageButton? = null
    private var btnMiniExpand: ImageButton? = null
    private val miniPlayerHandler = Handler(Looper.getMainLooper())
    private val miniPlayerProgressRunnable = object : Runnable {
        override fun run() {
            updateMiniPlayerProgress()
            miniPlayerHandler.postDelayed(this, 500)
        }
    }

    // 播放队列相关
    private var rvPlaylist: RecyclerView? = null
    private var playlistAdapter: PlaylistAdapter? = null

    var musicService: MusicService? = null
    private var serviceBound = false

    private var miniCoverRotationAnimator: ValueAnimator? = null
    
    // Broadcast receiver for background changes
    private val backgroundChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.melodyflow.app.BACKGROUND_CHANGED") {
                com.melodyflow.app.util.BackgroundManager.clearCache()
                com.melodyflow.app.util.BackgroundManager.applyToActivity(this@MainActivity)
            }
        }
    }

    // Keep a reference to the listener so it can be properly removed
    private val miniPlayerListener = object : MusicService.PlaybackListener {
        override fun onSongChanged(song: Song?) {
            updateMiniPlayer()
            updatePlaylistHighlight()
        }
        override fun onPlayStateChanged(isPlaying: Boolean) {
            updateMiniPlayer()
            if (isPlaying) {
                startMiniPlayerProgressUpdate()
            }
        }
        override fun onError(message: String) {
            // Mini player doesn't need to show errors
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
            // Add playback listener for real-time updates
            musicService?.addPlaybackListener(miniPlayerListener)
            updateMiniPlayer()
            initPlaylistRecyclerView()
            updatePlaylistData()
            startMiniPlayerProgressUpdate()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.removePlaybackListener(miniPlayerListener)
            musicService = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMusicService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        // Register background change receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(backgroundChangeReceiver, IntentFilter("com.melodyflow.app.BACKGROUND_CHANGED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(backgroundChangeReceiver, IntentFilter("com.melodyflow.app.BACKGROUND_CHANGED"))
        }

        initViews()
        setupViewPager()
        setupBottomNav()
        setupMiniPlayer()
        setupAnimations()

        // 检查启动页面设置 或 外部跳转
        val navTo = intent.getStringExtra("nav_to")
        val prefs = getSharedPreferences("MelodyFlow", MODE_PRIVATE)
        val startupPage = prefs.getString("startup_page", "home") ?: "home"
        val startPosition = when {
            navTo == "ai" -> 3
            startupPage == "ai" -> 3
            else -> 0
        }
        viewPager.setCurrentItem(startPosition, false)
        updateNavSelection(startPosition)
        bottomNav?.selectedItemId = when (startPosition) {
            0 -> R.id.nav_home
            1 -> R.id.nav_search
            2 -> R.id.nav_library
            3 -> R.id.nav_ai
            else -> R.id.nav_home
        }

        checkPermissions()
        checkAndShowInfoDialog()
    }

    private fun checkAndShowInfoDialog() {
        val prefs = getSharedPreferences("MelodyFlow", MODE_PRIVATE)
        val lastVersionCode = prefs.getInt("last_version_code", 0)
        val currentVersionCode = BuildConfig.VERSION_CODE
        val hasAgreed = prefs.getBoolean("has_agreed_to_terms", false)

        when {
            !hasAgreed -> {
                // 首次使用，显示用户协议
                val intent = Intent(this, InfoDialogActivity::class.java).apply {
                    putExtra(InfoDialogActivity.EXTRA_FIRST_TIME, true)
                }
                startActivity(intent)
                // 首次使用时同时设置同意协议和当前版本号，避免下次启动误判为版本更新
                prefs.edit()
                    .putBoolean("has_agreed_to_terms", true)
                    .putInt("last_version_code", currentVersionCode)
                    .apply()
            }
            currentVersionCode > lastVersionCode -> {
                // 版本更新，显示更新日志和捐赠提示
                val intent = Intent(this, InfoDialogActivity::class.java).apply {
                    putExtra(InfoDialogActivity.EXTRA_SHOW_ONLY_CHANGELOG, true)
                    putExtra(InfoDialogActivity.EXTRA_SHOW_PROMOTION, true)
                }
                startActivity(intent)
                // 延迟更新版本号，确保弹窗显示完成
                Handler(mainLooper).postDelayed({
                    prefs.edit().putInt("last_version_code", currentVersionCode).apply()
                }, 500)
                return
            }
        }

        // 更新最后版本号（仅在非更新弹窗情况下）
        prefs.edit().putInt("last_version_code", currentVersionCode).apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle landscape: bottom nav may be replaced by Navigation Rail in layout
        // Update mini player visibility for landscape
        updateMiniPlayer()
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
    }

    override fun onPause() {
        super.onPause()
        // Proper lifecycle: pause heavy operations
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMiniPlayerProgressUpdate()
        musicService?.removePlaybackListener(miniPlayerListener)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        // Unregister background change receiver
        unregisterReceiver(backgroundChangeReceiver)
    }

    private lateinit var miniCoverContainer: View
    private var ivMiniCoverInternal: ImageView? = null

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)

        // Handle both portrait and landscape layouts
        bottomNav = findViewById(R.id.bottomNav)
        navigationRail = null // We use custom nav rail in landscape

        miniPlayer = findViewById(R.id.miniPlayer) ?: findViewById(R.id.miniPlayerContainer)
        miniCoverContainer = miniPlayer.findViewById(R.id.miniCoverContainer)
        ivMiniCover = miniPlayer.findViewById(R.id.ivMiniCover)
        // 获取内部ImageView用于旋转动画
        ivMiniCoverInternal = miniPlayer.findViewById(R.id.ivMiniCover)
        tvMiniTitle = miniPlayer.findViewById(R.id.tvMiniTitle)
        tvMiniArtist = miniPlayer.findViewById(R.id.tvMiniArtist)
        btnMiniPlay = miniPlayer.findViewById(R.id.btnMiniPlay)
        btnMiniNext = miniPlayer.findViewById(R.id.btnMiniNext)
        btnMiniPrevious = miniPlayer.findViewById(R.id.btnMiniPrevious)

        // 初始化横屏迷你播放器控件
        seekBarMini = miniPlayer.findViewById(R.id.seekBarMini)
        tvMiniCurrentTime = miniPlayer.findViewById(R.id.tvMiniCurrentTime)
        tvMiniTotalTime = miniPlayer.findViewById(R.id.tvMiniTotalTime)
        btnMiniPlaylist = miniPlayer.findViewById(R.id.btnMiniPlaylist)
        btnMiniExpand = miniPlayer.findViewById(R.id.btnMiniExpand)

        // Initialize landscape navigation buttons if they exist
        val navHome = findViewById<View?>(R.id.navHome)
        val navSearch = findViewById<View?>(R.id.navSearch)
        val navLibrary = findViewById<View?>(R.id.navLibrary)
        val navAI = findViewById<View?>(R.id.navAI)
        val navSettings = findViewById<View?>(R.id.navSettings)
        
        navHome?.setOnClickListener {
            viewPager.setCurrentItem(0, true)
            updateNavSelection(0)
        }
        navSearch?.setOnClickListener {
            viewPager.setCurrentItem(1, true)
            updateNavSelection(1)
        }
        navLibrary?.setOnClickListener {
            viewPager.setCurrentItem(2, true)
            updateNavSelection(2)
        }
        navAI?.setOnClickListener {
            viewPager.setCurrentItem(3, true)
            updateNavSelection(3)
        }
        navSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateNavSelection(position: Int) {
        // Update landscape nav icons color
        val navHome = findViewById<View?>(R.id.navHome)
        val navSearch = findViewById<View?>(R.id.navSearch)
        val navLibrary = findViewById<View?>(R.id.navLibrary)
        val navAI = findViewById<View?>(R.id.navAI)
        val navSettings = findViewById<View?>(R.id.navSettings)

        // Get colors
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val textSecondaryColor = ContextCompat.getColor(this, R.color.text_secondary)

        // Update navHome
        navHome?.findViewById<ImageView>(R.id.navHomeIcon)?.let {
            it.setColorFilter(if (position == 0) primaryColor else textSecondaryColor)
        }

        // Update navSearch
        navSearch?.findViewById<ImageView>(R.id.navSearchIcon)?.let {
            it.setColorFilter(if (position == 1) primaryColor else textSecondaryColor)
        }

        // Update navLibrary
        navLibrary?.findViewById<ImageView>(R.id.navLibraryIcon)?.let {
            it.setColorFilter(if (position == 2) primaryColor else textSecondaryColor)
        }

        // Update navAI
        navAI?.findViewById<ImageView>(R.id.navAIIcon)?.let {
            it.setColorFilter(if (position == 3) primaryColor else textSecondaryColor)
        }

        // Update navSettings
        navSettings?.findViewById<ImageView>(R.id.navSettingsIcon)?.let {
            it.setColorFilter(textSecondaryColor)
        }
    }

    private fun setupAnimations() {
        // 设置迷你播放器封面旋转动画 - 只旋转内部ImageView
        miniCoverRotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 20000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = null
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                // 旋转内部的ImageView，保持容器不动
                ivMiniCoverInternal?.rotation = value
            }
        }
    }

    private fun updateMiniCoverAnimation(isPlaying: Boolean) {
        if (isPlaying) {
            if (miniCoverRotationAnimator?.isRunning != true) {
                miniCoverRotationAnimator?.start()
            }
        } else {
            miniCoverRotationAnimator?.pause()
        }
    }

    private fun animateButtonPress(view: View) {
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f).apply {
            duration = 100
            interpolator = DecelerateInterpolator()
        }
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f).apply {
            duration = 100
            interpolator = DecelerateInterpolator()
        }
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.9f, 1f).apply {
            duration = 150
            interpolator = OvershootInterpolator(1.5f)
            startDelay = 100
        }
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.9f, 1f).apply {
            duration = 150
            interpolator = OvershootInterpolator(1.5f)
            startDelay = 100
        }
        AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY)
            playTogether(scaleUp, scaleUpY)
            start()
        }
    }

    private fun animateMiniPlayerShow() {
        miniPlayer.alpha = 0f
        miniPlayer.translationY = 100f
        miniPlayer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun animateMiniPlayerHide() {
        miniPlayer.animate()
            .alpha(0f)
            .translationY(100f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun setupViewPager() {
        viewPager.adapter = MainPagerAdapter(this)
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = 4

        // 优化的页面堆叠切换动画 - 视差效果
        viewPager.setPageTransformer { page, position ->
            val absPos = kotlin.math.abs(position)
            when {
                position < -1.5 -> {
                    page.alpha = 0f
                    page.translationX = 0f
                }
                position <= 1.5 -> {
                    // 当前页面的视差效果
                    val parallaxFactor = 0.8f
                    val pageWidth = page.width.toFloat()
                    val currentPageTranslation = if (position > 0) {
                        pageWidth * -position * parallaxFactor
                    } else {
                        pageWidth * -position * 0.1f
                    }

                    page.apply {
                        // 透明度渐变：当前页1.0，相邻页0.6，更远的页0.3
                        alpha = when {
                            absPos < 0.5f -> 1f
                            absPos < 1f -> 0.7f - (absPos - 0.5f) * 0.4f
                            else -> 0.4f - (absPos - 1f) * 0.4f
                        }.coerceIn(0.3f, 1f)

                        // 水平位移 - 堆叠效果的核心
                        translationX = currentPageTranslation

                        // 缩放效果 - 相邻页面稍小
                        scaleX = (1f - absPos * 0.12f).coerceAtLeast(0.88f)
                        scaleY = (1f - absPos * 0.12f).coerceAtLeast(0.88f)

                        // Z轴深度效果
                        translationZ = -absPos * 10f

                        // 卡片阴影模拟（通过scale和alpha组合）
                        val shadowAlpha = (1f - absPos * 0.5f).coerceAtLeast(0.5f)
                    }
                }
                else -> {
                    page.alpha = 0f
                    page.translationX = 0f
                }
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val itemId = when (position) {
                    0 -> R.id.nav_home
                    1 -> R.id.nav_search
                    2 -> R.id.nav_library
                    3 -> R.id.nav_ai
                    else -> R.id.nav_home
                }
                bottomNav?.selectedItemId = itemId
                // Update landscape nav selection
                updateNavSelection(position)
            }
        })
    }

    private fun setupBottomNav() {
        bottomNav?.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_search -> 1
                R.id.nav_library -> 2
                R.id.nav_ai -> 3
                else -> 0
            }
            viewPager.setCurrentItem(position, true)
            true
        }

        // Custom nav rail handling is done separately if needed
    }

    private fun setupMiniPlayer() {
        miniPlayer.setOnClickListener {
            animateButtonPress(miniPlayer)
            startActivity(Intent(this, PlayerActivity::class.java))
            overridePendingTransition(R.anim.slide_up_fade_in, R.anim.slide_down_fade_out)
        }

        btnMiniPlay.setOnClickListener {
            animateButtonPress(btnMiniPlay)
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_PAUSE
            }
            startService(intent)
            updateMiniPlayer()
        }

        btnMiniNext.setOnClickListener {
            animateButtonPress(btnMiniNext)
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            startService(intent)
            updateMiniPlayer()
        }

        btnMiniPrevious.setOnClickListener {
            animateButtonPress(btnMiniPrevious)
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            startService(intent)
            updateMiniPlayer()
        }

        // 横屏迷你播放器额外按钮
        btnMiniPlaylist?.setOnClickListener {
            animateButtonPress(it)
            showMiniPlaylistDialog()
        }

        btnMiniExpand?.setOnClickListener {
            animateButtonPress(it)
            startActivity(Intent(this, PlayerActivity::class.java))
            overridePendingTransition(R.anim.slide_up_fade_in, R.anim.slide_down_fade_out)
        }

        // 设置进度条监听器
        setupMiniPlayerSeekBar()
    }

    private fun setupMiniPlayerSeekBar() {
        seekBarMini?.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                tvMiniCurrentTime?.text = formatTime(value.toInt())
            }
        }

        seekBarMini?.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                musicService?.seekTo(slider.value.toInt())
            }
        })
    }

    private fun startMiniPlayerProgressUpdate() {
        miniPlayerHandler.removeCallbacks(miniPlayerProgressRunnable)
        miniPlayerHandler.post(miniPlayerProgressRunnable)
    }

    private fun stopMiniPlayerProgressUpdate() {
        miniPlayerHandler.removeCallbacks(miniPlayerProgressRunnable)
    }

    private fun updateMiniPlayerProgress() {
        musicService?.let { service ->
            try {
                val position = service.getCurrentPosition()
                val duration = service.getDuration()

                // 更新进度条最大值
                if (duration > 0) {
                    seekBarMini?.valueTo = duration.toFloat()
                    tvMiniTotalTime?.text = formatTime(duration)
                }

                // 更新当前进度
                if (duration > 0 || position > 0) {
                    val safePosition = position.coerceIn(0, duration.coerceAtLeast(0))
                    seekBarMini?.value = safePosition.toFloat()
                    tvMiniCurrentTime?.text = formatTime(safePosition)
                }
            } catch (e: Exception) {
                // 播放器未准备好，跳过更新
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun showMiniPlaylistDialog() {
        val playlist = musicService?.getPlaylist() ?: emptyList()
        if (playlist.isEmpty()) {
            android.widget.Toast.makeText(this, "播放列表为空", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_playlist, null)
        dialog.setContentView(view)

        val rvPlaylist = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPlaylist)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        tvTitle?.text = "播放列表 (${playlist.size})"

        val currentSongId = musicService?.getCurrentSong()?.id
        val adapter = PlaylistAdapter(currentSongId) { song ->
            musicService?.playSong(song)
            dialog.dismiss()
        }
        adapter.submitList(playlist)

        rvPlaylist?.layoutManager = LinearLayoutManager(this)
        rvPlaylist?.adapter = adapter
        rvPlaylist?.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startMusicService()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startMusicService()
        }
    }

    private fun startMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun updateMiniPlayer() {
        musicService?.let { service ->
            val song = service.getCurrentSong()
            if (song != null) {
                if (miniPlayer.visibility != View.VISIBLE) {
                    miniPlayer.visibility = View.VISIBLE
                    animateMiniPlayerShow()
                }
                tvMiniTitle.text = song.name
                tvMiniArtist.text = song.artist

                val coverSource = if (song.pic.isNotBlank() && !song.pic.startsWith("http")) {
                    val picFile = java.io.File(song.pic)
                    if (picFile.exists()) {
                        android.util.Log.i("MainActivity", "Loading local cover for ${song.name}: ${song.pic}")
                        android.net.Uri.fromFile(picFile)
                    } else {
                        android.util.Log.w("MainActivity", "Local cover file not found: ${song.pic}, falling back to URL")
                        song.getCoverUrl()
                    }
                } else {
                    song.getCoverUrl()
                }
                android.util.Log.i("MainActivity", "Cover source for ${song.name}: $coverSource")
                
                Glide.with(this)
                    .load(coverSource)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivMiniCover)

                btnMiniPlay.setImageResource(
                    if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
                )

                updateMiniCoverAnimation(service.isPlaying())

                // 更新迷你播放器进度条
                updateMiniPlayerProgress()
            } else {
                if (miniPlayer.visibility == View.VISIBLE) {
                    animateMiniPlayerHide()
                    miniPlayer.postDelayed({
                        miniPlayer.visibility = View.GONE
                    }, 250)
                }
                miniCoverRotationAnimator?.cancel()
            }
        }
    }

    fun navigateToPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // 初始化播放队列RecyclerView
    private fun initPlaylistRecyclerView() {
        rvPlaylist = findViewById(R.id.rvPlaylist)
        rvPlaylist?.let { recyclerView ->
            recyclerView.layoutManager = LinearLayoutManager(this)
            playlistAdapter = PlaylistAdapter(
                currentSongId = musicService?.getCurrentSong()?.id,
                onItemClick = { song ->
                    musicService?.playSong(song)
                }
            )
            recyclerView.adapter = playlistAdapter
        }
    }

    // 更新播放队列数据
    private fun updatePlaylistData() {
        musicService?.let { service ->
            val playlist = service.getPlaylist()
            playlistAdapter?.submitList(playlist.toList())
        }
    }

    // 更新播放队列高亮
    private fun updatePlaylistHighlight() {
        val currentSong = musicService?.getCurrentSong()
        playlistAdapter?.let { adapter ->
            // 重新创建适配器以更新当前播放歌曲高亮
            val newAdapter = PlaylistAdapter(
                currentSongId = currentSong?.id,
                onItemClick = { song ->
                    musicService?.playSong(song)
                }
            )
            rvPlaylist?.adapter = newAdapter
            playlistAdapter = newAdapter
            updatePlaylistData()
        }
    }
}
