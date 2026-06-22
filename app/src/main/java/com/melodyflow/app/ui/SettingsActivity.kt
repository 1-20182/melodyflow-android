package com.melodyflow.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.melodyflow.app.BuildConfig
import com.melodyflow.app.R
import com.melodyflow.app.data.BackupManager
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.data.LocalScanService
import com.melodyflow.app.util.UpdateChecker
import com.melodyflow.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvCacheSize: TextView
    private lateinit var tvMusicCacheSize: TextView
    private lateinit var tvTotalCacheSize: TextView
    private lateinit var sliderCacheLimit: Slider
    private lateinit var tvCacheLimitValue: TextView
    private lateinit var btnClearAllCache: MaterialButton
    private lateinit var btnClearCache: MaterialButton
    private lateinit var btnClearMusicCache: MaterialButton
    private lateinit var btnExportBackup: MaterialButton
    private lateinit var btnImportBackup: MaterialButton
    private lateinit var switchAutoBackup: SwitchMaterial
    private lateinit var layoutCustomScanDirs: LinearLayout
    private lateinit var tvNoCustomDirs: TextView
    private lateinit var tvDefaultScanDirs: TextView
    private lateinit var btnAddScanDir: MaterialButton
    private lateinit var ivBackgroundPreview: ImageView
    private lateinit var btnSelectBackground: MaterialButton
    private lateinit var btnResetBackground: MaterialButton
    private lateinit var gradientPresetGroup: RadioGroup
    private lateinit var rgStartupPage: RadioGroup
    private lateinit var rbStartupHome: RadioButton
    private lateinit var rbStartupAI: RadioButton

    private val bgFile: File get() = File(filesDir, "custom_background.jpg")
    private val cacheManager: CacheManager by lazy { CacheManager.getInstance(this) }
    private val backupManager: BackupManager by lazy { BackupManager.getInstance(this) }
    private val localScanService: LocalScanService by lazy { LocalScanService.getInstance(this) }

    private lateinit var viewModel: SettingsViewModel

    private val gradientPresets = listOf(
        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt()),    // Purple gradient
        intArrayOf(0xFFF093FB.toInt(), 0xFFF5576C.toInt()),    // Pink gradient
        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt()),    // Blue gradient
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),    // Green gradient
        intArrayOf(0xFFFF9A9E.toInt(), 0xFFFECFEF.toInt()),    // Peach gradient
        intArrayOf(0xFFFA709A.toInt(), 0xFFFEE140.toInt())     // Orange gradient
    )

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveBackground(it) }
    }

    private val pickBackupLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importBackupFromUri(it) }
    }

    private val pickDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { addScanDirectory(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // --- Cache Management ---
        tvCacheSize = findViewById(R.id.tvCacheSize)
        tvMusicCacheSize = findViewById(R.id.tvMusicCacheSize)
        tvTotalCacheSize = findViewById(R.id.tvTotalCacheSize)
        sliderCacheLimit = findViewById(R.id.sliderCacheLimit)
        tvCacheLimitValue = findViewById(R.id.tvCacheLimitValue)
        btnClearAllCache = findViewById(R.id.btnClearAllCache)
        btnClearCache = findViewById(R.id.btnClearCache)
        btnClearMusicCache = findViewById(R.id.btnClearMusicCache)

        // --- Backup & Restore ---
        btnExportBackup = findViewById(R.id.btnExportBackup)
        btnImportBackup = findViewById(R.id.btnImportBackup)
        switchAutoBackup = findViewById(R.id.switchAutoBackup)

        // --- Scan Directory Management ---
        layoutCustomScanDirs = findViewById(R.id.layoutCustomScanDirs)
        tvNoCustomDirs = findViewById(R.id.tvNoCustomDirs)
        tvDefaultScanDirs = findViewById(R.id.tvDefaultScanDirs)
        btnAddScanDir = findViewById(R.id.btnAddScanDir)

        // --- Background ---
        ivBackgroundPreview = findViewById(R.id.ivBackgroundPreview)
        btnSelectBackground = findViewById(R.id.btnSelectBackground)
        btnResetBackground = findViewById(R.id.btnResetBackground)
        gradientPresetGroup = findViewById(R.id.gradientPresetGroup)

        // --- Startup Page ---
        rgStartupPage = findViewById(R.id.rgStartupPage)
        rbStartupHome = findViewById(R.id.rbStartupHome)
        rbStartupAI = findViewById(R.id.rbStartupAI)

        // Initialize data
        updateCacheSize()
        setupCacheLimitSlider()
        loadBackgroundPreview()
        setupGradientPresets()
        setupStartupPageSettings()
        setupVersionDisplay()
        setupBackupSettings()
        setupScanDirectoryManagement()

        // --- Click listeners ---
        btnClearAllCache.setOnClickListener { clearAllCache() }
        btnClearCache.setOnClickListener { clearCache() }
        btnClearMusicCache.setOnClickListener { clearMusicCache() }
        btnExportBackup.setOnClickListener { exportBackup() }
        btnImportBackup.setOnClickListener { importBackup() }
        btnSelectBackground.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        btnResetBackground.setOnClickListener { resetBackground() }
        btnAddScanDir.setOnClickListener {
            pickDirLauncher.launch(null)
        }
        gradientPresetGroup.setOnCheckedChangeListener { _, checkedId ->
            applyGradientPreset(checkedId)
        }

        // AI Settings button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAISettings)?.setOnClickListener {
            startActivity(Intent(this, AISettingsActivity::class.java))
        }

        // View Changelog button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewChangelog)?.setOnClickListener {
            startActivity(Intent(this, InfoDialogActivity::class.java).apply {
                putExtra(InfoDialogActivity.EXTRA_SHOW_ONLY_CHANGELOG, true)
            })
        }

        // Check Update button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCheckUpdate)?.setOnClickListener {
            checkForUpdate()
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Update music cache info from ViewModel
                    tvMusicCacheSize.text = "${formatSize(state.cacheSize)} (${state.cacheCount} 首歌曲)"

                    // Update cache limit slider
                    sliderCacheLimit.value = state.cacheLimitMb.toFloat().coerceIn(100f, 2000f)
                    tvCacheLimitValue.text = "${state.cacheLimitMb} MB"

                    // Update auto backup switch
                    switchAutoBackup.isChecked = state.isAutoBackupEnabled

                    // Update scan directories
                    refreshCustomScanDirsFromState(state.scanDirectories)
                }
            }
        }
    }

    // ============================================================
    // Cache Management
    // ============================================================

    private fun setupCacheLimitSlider() {
        sliderCacheLimit.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val limitMb = value.toInt()
                tvCacheLimitValue.text = "${limitMb} MB"
            }
        }

        sliderCacheLimit.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val limitMb = slider.value.toInt()
                viewModel.setCacheLimitMb(limitMb)
                // Enforce the new limit
                lifecycleScope.launch {
                    cacheManager.enforceCacheLimit()
                    updateMusicCacheSize()
                    updateTotalCacheSize()
                }
            }
        })
    }

    private fun updateTotalCacheSize() {
        CoroutineScope(Dispatchers.IO).launch {
            var totalSize = 0L

            // Image cache size
            val imageCacheDir = File(applicationContext.cacheDir, "image_manager_disk_cache")
            if (imageCacheDir.exists()) {
                totalSize += getFolderSize(imageCacheDir)
            }

            // Music cache size
            totalSize += cacheManager.getCacheSize()

            val formatted = formatSize(totalSize)
            withContext(Dispatchers.Main) {
                tvTotalCacheSize.text = formatted
            }
        }
    }

    private fun clearAllCache() {
        AlertDialog.Builder(this)
            .setTitle("清除全部缓存")
            .setMessage("确定要清除所有缓存（图片缓存和音乐缓存）吗？此操作不可撤销。")
            .setPositiveButton("清除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        // Clear image cache
                        try {
                            com.bumptech.glide.Glide.get(this@SettingsActivity).clearDiskCache()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        // Clear music cache via ViewModel
                        viewModel.clearAllCache()
                    }
                    updateCacheSize()
                    updateTotalCacheSize()
                    Toast.makeText(this@SettingsActivity, "全部缓存已清除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupGradientPresets() {
        gradientPresets.forEachIndexed { index, colors ->
            val radioButton = RadioButton(this).apply {
                id = index + 1
                buttonDrawable = null
                setPadding(8, 8, 8, 8)
            }
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                colors
            ).apply {
                cornerRadius = 8f
            }
            radioButton.background = gradientDrawable
            gradientPresetGroup.addView(radioButton)
        }
    }

    private fun setupStartupPageSettings() {
        val prefs = getSharedPreferences("MelodyFlow", MODE_PRIVATE)
        val startupPage = prefs.getString("startup_page", "home") ?: "home"

        // 设置初始选中状态
        when (startupPage) {
            "ai" -> rbStartupAI.isChecked = true
            else -> rbStartupHome.isChecked = true
        }

        // 监听选择变化
        rgStartupPage.setOnCheckedChangeListener { _, checkedId ->
            val selectedPage = when (checkedId) {
                R.id.rbStartupAI -> "ai"
                else -> "home"
            }
            prefs.edit().putString("startup_page", selectedPage).apply()
            Toast.makeText(this, "启动页面已设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupVersionDisplay() {
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = getString(R.string.setting_version, BuildConfig.VERSION_NAME)
    }

    // ============================================================
    // Backup & Restore
    // ============================================================

    private fun setupBackupSettings() {
        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoBackupEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "自动备份已开启" else "自动备份已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportBackup() {
        btnExportBackup.isEnabled = false
        btnExportBackup.text = "导出中..."

        lifecycleScope.launch {
            try {
                val file = backupManager.exportBackup()
                Toast.makeText(
                    this@SettingsActivity,
                    "备份已导出至: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnExportBackup.isEnabled = true
                btnExportBackup.text = "导出备份"
            }
        }
    }

    private fun importBackup() {
        pickBackupLauncher.launch("application/json")
    }

    private fun importBackupFromUri(uri: Uri) {
        btnImportBackup.isEnabled = false
        btnImportBackup.text = "导入中..."

        lifecycleScope.launch {
            try {
                val file = copyUriToTempFile(uri)
                if (file == null) {
                    Toast.makeText(this@SettingsActivity, "无法读取备份文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Preview the backup first
                val preview = backupManager.previewBackup(file)
                if (preview == null) {
                    Toast.makeText(this@SettingsActivity, "无效的备份文件", Toast.LENGTH_SHORT).show()
                    file.delete()
                    return@launch
                }

                // Show confirmation dialog with preview info
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date(preview.exportDate))
                val message = buildString {
                    append("备份日期: $dateStr\n")
                    append("收藏: ${preview.favoriteCount} 首\n")
                    append("播放历史: ${preview.historyCount} 条\n")
                    append("日历事件: ${preview.calendarEventCount} 个\n")
                    append("日记: ${preview.diaryCount} 篇\n")
                    append("AI配置: ${if (preview.hasAiConfig) "有" else "无"}\n\n")
                    append("确定要导入此备份吗？")
                }

                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("导入备份")
                    .setMessage(message)
                    .setPositiveButton("导入") { _, _ ->
                        lifecycleScope.launch {
                            val success = backupManager.importBackup(
                                file,
                                BackupManager.ImportOptions()
                            )
                            if (success) {
                                Toast.makeText(this@SettingsActivity, "备份导入成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@SettingsActivity, "备份导入失败", Toast.LENGTH_SHORT).show()
                            }
                            file.delete()
                        }
                    }
                    .setNegativeButton("取消") { _, _ ->
                        file.delete()
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnImportBackup.isEnabled = true
                btnImportBackup.text = "导入备份"
            }
        }
    }

    private suspend fun copyUriToTempFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val tempFile = File(cacheDir, "import_backup_temp.json")
            tempFile.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    // Scan Directory Management
    // ============================================================

    private fun setupScanDirectoryManagement() {
        // Display default directories
        val defaultDirs = localScanService.getDefaultScanDirectories()
        if (defaultDirs.isEmpty()) {
            tvDefaultScanDirs.text = "无可用默认目录"
        } else {
            tvDefaultScanDirs.text = defaultDirs.joinToString("\n") { it.absolutePath }
        }

        // Custom directories are loaded from ViewModel state in observeViewModel()
    }

    private fun refreshCustomScanDirsFromState(scanDirs: List<String>) {
        layoutCustomScanDirs.removeAllViews()

        if (scanDirs.isEmpty()) {
            tvNoCustomDirs.visibility = View.VISIBLE
        } else {
            tvNoCustomDirs.visibility = View.GONE
            for (dirPath in scanDirs) {
                val itemView = layoutInflater.inflate(
                    android.R.layout.simple_list_item_2,
                    layoutCustomScanDirs,
                    false
                )
                val dirFile = File(dirPath)
                itemView.findViewById<TextView>(android.R.id.text1).apply {
                    text = dirFile.name
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 14f
                }
                itemView.findViewById<TextView>(android.R.id.text2).apply {
                    text = dirPath
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 12f
                }
                itemView.setOnClickListener {
                    showRemoveDirectoryDialog(dirPath)
                }
                layoutCustomScanDirs.addView(itemView)
            }
        }
    }

    private fun addScanDirectory(uri: Uri) {
        // Convert tree URI to a file path
        val path = uri.path ?: return
        // Extract the actual path from the document tree URI
        // Format: /tree/primary:Music -> /storage/emulated/0/Music
        val filePath = convertUriToFilePath(path)
        if (filePath == null) {
            Toast.makeText(this, "无法识别目录路径", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addScanDirectory(filePath)
        Toast.makeText(this, "目录已添加: $filePath", Toast.LENGTH_SHORT).show()
    }

    private fun convertUriToFilePath(uriPath: String): String? {
        // Handle common URI path formats from SAF
        // /tree/primary:Music -> /storage/emulated/0/Music
        // /tree/XXXX-XXXX:Music -> /storage/XXXX-XXXX/Music
        val colonIndex = uriPath.indexOf(':')
        if (colonIndex == -1) return null

        val treePrefix = uriPath.substring(0, colonIndex)
        val relativePath = uriPath.substring(colonIndex + 1)

        val basePath = when {
            treePrefix.endsWith("/primary") -> "/storage/emulated/0"
            treePrefix.contains("/") -> {
                // Extract volume ID like /tree/XXXX-XXXX -> /storage/XXXX-XXXX
                val volumeId = treePrefix.substringAfterLast("/")
                "/storage/$volumeId"
            }
            else -> return null
        }

        return if (relativePath.isNotEmpty()) "$basePath/$relativePath" else basePath
    }

    private fun showRemoveDirectoryDialog(dirPath: String) {
        AlertDialog.Builder(this)
            .setTitle("移除目录")
            .setMessage("确定要从扫描目录中移除以下路径吗？\n\n$dirPath")
            .setPositiveButton("移除") { _, _ ->
                viewModel.removeScanDirectory(dirPath)
                Toast.makeText(this, "目录已移除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    // Background & Gradient
    // ============================================================

    private fun applyGradientPreset(checkedId: Int) {
        val index = checkedId - 1
        if (index >= 0 && index < gradientPresets.size) {
            val editor = getSharedPreferences("MelodyFlow", MODE_PRIVATE).edit()
            editor.putInt("gradient_start", gradientPresets[index][0])
            editor.putInt("gradient_end", gradientPresets[index][1])
            editor.apply()

            // 删除旧的图片背景，确保渐变优先
            if (bgFile.exists()) bgFile.delete()

            // 清除缓存并立即应用到当前 Activity
            com.melodyflow.app.util.BackgroundManager.clearCache()
            com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

            // 通知其他 Activity 更新背景
            sendBroadcast(Intent("com.melodyflow.app.BACKGROUND_CHANGED"))

            loadBackgroundPreview()
            Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCacheSize() {
        try {
            val cacheDir = File(applicationContext.cacheDir, "image_manager_disk_cache")
            if (cacheDir.exists()) {
                val size = getFolderSize(cacheDir)
                tvCacheSize.text = formatSize(size)
            } else {
                tvCacheSize.text = "0 MB"
            }
        } catch (e: Exception) {
            tvCacheSize.text = "未知"
        }
    }

    private fun updateMusicCacheSize() {
        CoroutineScope(Dispatchers.IO).launch {
            val size = cacheManager.getCacheSize()
            val count = cacheManager.getCacheCount()
            withContext(Dispatchers.Main) {
                tvMusicCacheSize.text = "${formatSize(size)} ($count 首歌曲)"
            }
        }
    }

    private fun clearCache() {
        Thread {
            try {
                com.bumptech.glide.Glide.get(this@SettingsActivity).clearDiskCache()
                runOnUiThread {
                    updateCacheSize()
                    updateTotalCacheSize()
                    Toast.makeText(this, "图片缓存已清除", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "清除失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun clearMusicCache() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.clearAllCache()
            withContext(Dispatchers.Main) {
                updateMusicCacheSize()
                updateTotalCacheSize()
                Toast.makeText(this@SettingsActivity, "音乐缓存已清除", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBackground(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                Toast.makeText(this, "设置失败，无法读取图片", Toast.LENGTH_SHORT).show()
                return
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, 1080, 1920, true)
            FileOutputStream(bgFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            gradientPresetGroup.clearCheck()

            // Clear background cache so new background takes effect immediately
            com.melodyflow.app.util.BackgroundManager.clearCache()
            // Apply new background to this activity
            com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

            // Send broadcast to notify other activities to update background
            sendBroadcast(Intent("com.melodyflow.app.BACKGROUND_CHANGED"))

            loadBackgroundPreview()
            Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBackgroundPreview() {
        try {
            val prefs = getSharedPreferences("MelodyFlow", MODE_PRIVATE)
            val gradientStart = prefs.getInt("gradient_start", 0)
            val gradientEnd = prefs.getInt("gradient_end", 0)

            if (bgFile.exists()) {
                com.bumptech.glide.Glide.with(this)
                    .load(bgFile)
                    .centerCrop()
                    .into(ivBackgroundPreview)
            } else if (gradientStart != 0 && gradientEnd != 0) {
                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(gradientStart, gradientEnd)
                ).apply {
                    cornerRadius = 12f
                }
                ivBackgroundPreview.background = gradientDrawable
                ivBackgroundPreview.setImageDrawable(null)
            } else {
                ivBackgroundPreview.setImageResource(R.drawable.ic_music_note)
                ivBackgroundPreview.setBackgroundColor(getColor(R.color.surface_variant))
            }
        } catch (e: Exception) {
            ivBackgroundPreview.visibility = View.GONE
        }
    }

    private fun resetBackground() {
        if (bgFile.exists()) bgFile.delete()
        val editor = getSharedPreferences("MelodyFlow", MODE_PRIVATE).edit()
        editor.remove("gradient_start")
        editor.remove("gradient_end")
        editor.apply()
        gradientPresetGroup.clearCheck()

        // Clear background cache and apply
        com.melodyflow.app.util.BackgroundManager.clearCache()
        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        // Send broadcast to notify other activities
        sendBroadcast(Intent("com.melodyflow.app.BACKGROUND_CHANGED"))

        loadBackgroundPreview()
        Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // Update Check
    // ============================================================

    private fun checkForUpdate() {
        val btn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCheckUpdate)
        btn?.isEnabled = false
        btn?.text = "检查中..."

        lifecycleScope.launch {
            val updateInfo = UpdateChecker().checkForUpdate()

            btn?.isEnabled = true
            btn?.text = "检查更新"

            if (!updateInfo.hasUpdate) {
                Toast.makeText(this@SettingsActivity, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                return@launch
            }

            showUpdateDialog(updateInfo.latestVersion, updateInfo.changelog, updateInfo.downloadUrl)
        }
    }

    private fun showUpdateDialog(version: String, changelog: String, downloadUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)
        dialogView.findViewById<TextView>(R.id.tvUpdateVersion).text = version
        dialogView.findViewById<TextView>(R.id.tvUpdateChangelog).text = changelog

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdateNow)
            .setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                startActivity(intent)
                dialog.dismiss()
            }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdateLater)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // ============================================================
    // Utility
    // ============================================================

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { size += getFolderSize(it) }
        } else {
            size += file.length()
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }
}
