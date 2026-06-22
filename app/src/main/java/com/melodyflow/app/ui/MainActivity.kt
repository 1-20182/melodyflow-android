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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.melodyflow.app.BuildConfig
import com.melodyflow.app.R
import com.melodyflow.app.adapter.MainPagerAdapter
import com.melodyflow.app.adapter.PlaylistAdapter
import com.melodyflow.app.data.BackupManager
import com.melodyflow.app.data.LocalScanService
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

    // StateFlow observation job references
    private var stateFlowObserverJob: kotlinx.coroutines.Job? = null

    private fun observeMusicServiceStateFlow() {
        stateFlowObserverJob?.cancel()
        stateFlowObserverJob = lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    musicService?.currentSongFlow?.collect { song ->
                        updateMiniPlayer()
                        updatePlaylistData()
                        updatePlaylistHighlight()
                    }
                }
                launch {
                    musicService?.isPlayingFlow?.collect { isPlaying ->
                        updateMiniPlayer()
                        if (isPlaying) {
                            startMiniPlayerProgressUpdate()
                        }
                    }
                }
                launch {
                    musicService?.playlistFlow?.collect {
                        updatePlaylistData()
                    }
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
            // Observe StateFlow for real-time updates
            observeMusicServiceStateFlow()
            updateMiniPlayer()
            initPlaylistRecyclerView()
            updatePlaylistData()
            startMiniPlayerProgressUpdate()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stateFlowObserverJob?.cancel()
            musicService = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether notification permission is granted or not, start music service
        startMusicService()
    }

    // Storage permission launcher for Android 10 and below
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (writeGranted || readGranted) {
            onStoragePermissionGranted()
        } else {
            // Permission denied, still start music service but skip local scan
            startMusicService()
        }
    }

    // Activity result launcher for MANAGE_EXTERNAL_STORAGE on Android 11+
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onStoragePermissionGranted()
            } else {
                // Permission denied, still start music service but skip local scan
                startMusicService()
            }
        }
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1001

        // Static flag to ensure incremental scan runs only once per app session (process lifetime)
        @Volatile
        private var hasScannedThisSession = false
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
        // Update background for dark/light mode switch
        com.melodyflow.app.util.BackgroundManager.updateForDarkMode(this)
        // Handle landscape: bottom nav may be replaced by Navigation Rail in layout
        // Update mini player visibility for landscape
        updateMiniPlayer()
        // Re-initialize playlist RecyclerView for landscape layout
        initPlaylistRecyclerView()
        updatePlaylistData()
        updatePlaylistHighlight()
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
        stateFlowObserverJob?.cancel()
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
        // Step 1: Check and request storage permission first
        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Use MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                onStoragePermissionGranted()
            } else {
                // Show rationale dialog before opening settings
                AlertDialog.Builder(this)
                    .setTitle("需要存储权限")
                    .setMessage("MelodyFlow 需要访问存储权限来扫描本地音乐文件和管理备份。请在设置中授予\"所有文件访问\"权限。")
                    .setPositiveButton("前往设置") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            // Fallback to generic settings
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    }
                    .setNegativeButton("稍后") { _, _ ->
                        // User declined, still start music service
                        startMusicService()
                    }
                    .setCancelable(false)
                    .show()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10: Use runtime permissions
            val writeGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val readGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (writeGranted && readGranted) {
                onStoragePermissionGranted()
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) || ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                ) {
                    // Show rationale
                    AlertDialog.Builder(this)
                        .setTitle("需要存储权限")
                        .setMessage("MelodyFlow 需要存储权限来扫描本地音乐文件和管理备份。")
                        .setPositiveButton("授权") { _, _ ->
                            storagePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                )
                            )
                        }
                        .setNegativeButton("稍后") { _, _ ->
                            startMusicService()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    // First time request
                    storagePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    )
                }
            }
        } else {
            // Below Android 6: permissions granted at install time
            onStoragePermissionGranted()
        }
    }

    /**
     * Called when storage permission is granted.
     * Triggers: notification permission check -> music service start -> auto scan -> backup check
     */
    private fun onStoragePermissionGranted() {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
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

        // Trigger incremental scan in background (only once per session)
        triggerIncrementalScan()

        // Check for backup restore opportunity
        checkBackupAndRestore()
    }

    /**
     * Trigger incremental scan on startup, only once per app session.
     * Uses a static flag (not SharedPreferences) so it resets when the app process is killed.
     */
    private fun triggerIncrementalScan() {
        if (hasScannedThisSession) return

        // Mark as scanned for this session
        hasScannedThisSession = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scanService = LocalScanService.getInstance(this@MainActivity)
                val count = scanService.incrementalScan()
                if (count > 0) {
                    android.util.Log.i("MainActivity", "Incremental scan found $count new songs")
                } else {
                    android.util.Log.i("MainActivity", "Incremental scan: no new songs found")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Incremental scan failed", e)
            }
        }
    }

    /**
     * Check if there is a backup file and the database is empty.
     * If so, show a dialog asking if the user wants to restore from backup.
     */
    private fun checkBackupAndRestore() {
        lifecycleScope.launch {
            try {
                val backupManager = BackupManager.getInstance(this@MainActivity)
                val hasBackup = withContext(Dispatchers.IO) { backupManager.hasBackupFile() }
                if (!hasBackup) return@launch

                val isDbEmpty = withContext(Dispatchers.IO) { backupManager.isDatabaseEmpty() }
                if (!isDbEmpty) return@launch

                // Both conditions met: backup exists and database is empty
                val backupFile = withContext(Dispatchers.IO) { backupManager.getLatestBackupFile() }
                    ?: return@launch

                val preview = withContext(Dispatchers.IO) { backupManager.previewBackup(backupFile) }
                    ?: return@launch

                // Build preview info text
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date(preview.exportDate))
                val infoBuilder = StringBuilder()
                infoBuilder.appendLine("备份时间: $dateStr")
                if (preview.favoriteCount > 0) infoBuilder.appendLine("收藏: ${preview.favoriteCount} 首")
                if (preview.historyCount > 0) infoBuilder.appendLine("历史: ${preview.historyCount} 条")
                if (preview.calendarEventCount > 0) infoBuilder.appendLine("日历事件: ${preview.calendarEventCount} 个")
                if (preview.diaryCount > 0) infoBuilder.appendLine("日记: ${preview.diaryCount} 篇")
                if (preview.hasAiConfig) infoBuilder.appendLine("AI 配置: 已包含")

                // Show restore dialog with custom view
                val dialogView = layoutInflater.inflate(R.layout.dialog_backup_restore, null)
                val tvBackupInfo = dialogView.findViewById<TextView>(R.id.tvBackupInfo)
                val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.progressBar)
                val tvStatus = dialogView.findViewById<TextView>(R.id.tvStatus)

                tvBackupInfo?.text = infoBuilder.toString().trim()
                progressBar?.visibility = View.GONE
                tvStatus?.visibility = View.GONE

                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("检测到备份数据")
                    .setView(dialogView)
                    .setPositiveButton("恢复", null)
                    .setNegativeButton("跳过", null)
                    .setCancelable(false)
                    .create()

                // Override positive button click to prevent auto-dismiss
                dialog.setOnShowListener {
                    val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    positiveButton.setOnClickListener {
                        // Disable buttons during restore
                        positiveButton.isEnabled = false
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false

                        // Show progress
                        progressBar?.visibility = View.VISIBLE
                        tvStatus?.visibility = View.VISIBLE
                        tvStatus?.text = "正在恢复数据..."

                        lifecycleScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                backupManager.importBackup(
                                    backupFile,
                                    BackupManager.ImportOptions()
                                )
                            }
                            if (success) {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "数据恢复成功",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "数据恢复失败",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                    }
                }

                dialog.show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Backup check failed", e)
            }
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
                    val service = musicService
                    if (service != null) {
                        service.playSong(song)
                    } else {
                        android.widget.Toast.makeText(this, "播放服务未就绪", android.widget.Toast.LENGTH_SHORT).show()
                    }
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
        val playlist = musicService?.getPlaylist() ?: emptyList()
        val position = playlist.indexOfFirst { it.id == currentSong?.id }
        playlistAdapter?.updateHighlight(position)
    }
}
