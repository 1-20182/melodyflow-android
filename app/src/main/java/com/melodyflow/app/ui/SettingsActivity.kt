package com.melodyflow.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.melodyflow.app.BuildConfig
import com.melodyflow.app.R
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvCacheSize: TextView
    private lateinit var tvMusicCacheSize: TextView
    private lateinit var btnClearCache: MaterialButton
    private lateinit var btnClearMusicCache: MaterialButton
    private lateinit var ivBackgroundPreview: ImageView
    private lateinit var btnSelectBackground: MaterialButton
    private lateinit var btnResetBackground: MaterialButton
    private lateinit var gradientPresetGroup: RadioGroup
    private lateinit var rgStartupPage: RadioGroup
    private lateinit var rbStartupHome: RadioButton
    private lateinit var rbStartupAI: RadioButton

    private val bgFile: File get() = File(filesDir, "custom_background.jpg")
    private val cacheManager: CacheManager by lazy { CacheManager.getInstance(this) }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tvCacheSize = findViewById(R.id.tvCacheSize)
        tvMusicCacheSize = findViewById(R.id.tvMusicCacheSize)
        btnClearCache = findViewById(R.id.btnClearCache)
        btnClearMusicCache = findViewById(R.id.btnClearMusicCache)
        ivBackgroundPreview = findViewById(R.id.ivBackgroundPreview)
        btnSelectBackground = findViewById(R.id.btnSelectBackground)
        btnResetBackground = findViewById(R.id.btnResetBackground)
        gradientPresetGroup = findViewById(R.id.gradientPresetGroup)
        rgStartupPage = findViewById(R.id.rgStartupPage)
        rbStartupHome = findViewById(R.id.rbStartupHome)
        rbStartupAI = findViewById(R.id.rbStartupAI)

        updateCacheSize()
        updateMusicCacheSize()
        loadBackgroundPreview()
        setupGradientPresets()
        setupStartupPageSettings()
        setupVersionDisplay()

        btnClearCache.setOnClickListener { clearCache() }
        btnClearMusicCache.setOnClickListener { clearMusicCache() }
        btnSelectBackground.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        btnResetBackground.setOnClickListener { resetBackground() }
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
    }

    override fun onResume() {
        super.onResume()
        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)
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
            cacheManager.clearAllCache()
            withContext(Dispatchers.Main) {
                updateMusicCacheSize()
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