package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.SongListHolder
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CachedSongsActivity : AppCompatActivity() {

    companion object {
        // 距离上次补全超过这个时间（毫秒）才重新补全，默认为30天
        private const val CACHE_COMPLETION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000L
    }

    private lateinit var rvSongs: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private lateinit var tvEmpty: TextView
    private val currentSongs = mutableListOf<Song>()

    private val cacheManager by lazy { (application as MelodyFlowApp).cacheManager }
    private val repository by lazy { (application as MelodyFlowApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_list)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.library_cached)
        setSupportActionBar(toolbar)

        rvSongs = findViewById(R.id.rvSongs)
        rvSongs.layoutManager = LinearLayoutManager(this)

        tvEmpty = findViewById(R.id.tvEmpty)

        songAdapter = SongAdapter(
            onItemClick = { song, position -> playSong(song, position) },
            onFavoriteClick = { song, isFavorite -> toggleFavorite(song, isFavorite) },
            showCacheIndicator = true
        )
        rvSongs.adapter = songAdapter

        setupSwipeToDelete()

        // 检查是否需要补全缓存信息（只在版本更新或间隔时间到达时执行）
        lifecycleScope.launch {
            checkAndCompleteCacheInfo()
            loadCachedSongs()
        }
    }

    private suspend fun checkAndCompleteCacheInfo() {
        val prefs = getSharedPreferences("MelodyFlow", MODE_PRIVATE)
        val lastCacheCompletionVersion = prefs.getInt("last_cache_completion_version", 0)
        val lastCacheCompletionTime = prefs.getLong("last_cache_completion_time", 0)
        val currentVersionCode = com.melodyflow.app.BuildConfig.VERSION_CODE
        val now = System.currentTimeMillis()

        // 判断是否需要执行补全：
        // 1. 版本号变化了（版本更新）
        // 2. 或者距离上次补全超过24小时
        val needsCompletion = currentVersionCode > lastCacheCompletionVersion ||
                (now - lastCacheCompletionTime > CACHE_COMPLETION_INTERVAL_MS)

        if (needsCompletion) {
            android.util.Log.i("CachedSongsActivity", "Checking cache completion. Current version: $currentVersionCode, last completion version: $lastCacheCompletionVersion")

            // Restore cached files from disk
            val restoredCount = cacheManager.restoreCachedFilesFromDisk()
            if (restoredCount > 0) {
                android.util.Log.i("CachedSongsActivity", "Restored $restoredCount cached files from disk")
            }

            // Check if there are any songs that need completion
            val needsCompletionCount = cacheManager.countNeedsCompletion()
            if (needsCompletionCount > 0) {
                // Complete missing cover/lyrics
                val completeResult = cacheManager.completeMissingCacheInfo { current, total ->
                    runOnUiThread {
                        tvEmpty.text = "正在补全缓存信息... ($current/$total)"
                        tvEmpty.visibility = View.VISIBLE
                        rvSongs.visibility = View.GONE
                    }
                }

                if (completeResult > 0) {
                    android.util.Log.i("CachedSongsActivity", "Completed $completeResult cache entries")
                }
            }

            // 更新补全状态
            prefs.edit()
                .putInt("last_cache_completion_version", currentVersionCode)
                .putLong("last_cache_completion_time", now)
                .apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_cached_songs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_backup -> {
                backupData()
                true
            }
            R.id.action_restore -> {
                showRestoreDialog()
                true
            }
            R.id.action_rescan -> {
                rescanAndComplete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun backupData() {
        lifecycleScope.launch {
            val path = cacheManager.backupCacheMetadata()
            if (path != null) {
                runOnUiThread {
                    Toast.makeText(this@CachedSongsActivity, "备份成功！\n文件：$path", Toast.LENGTH_LONG).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@CachedSongsActivity, "备份失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showRestoreDialog() {
        val backups = cacheManager.getAvailableBackups()
        
        if (backups.isEmpty()) {
            Toast.makeText(this, "未找到备份文件", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fileNames = backups.map { 
            it.substringAfterLast("/").substringAfterLast("\\") 
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("选择备份文件恢复")
            .setItems(fileNames) { _, which ->
                restoreFromBackup(backups[which])
            }
            .setPositiveButton("扫描全部备份") { _, _ ->
                scanAndRestore()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restoreFromBackup(backupPath: String) {
        lifecycleScope.launch {
            val count = cacheManager.restoreFromBackup(backupPath)
            runOnUiThread {
                if (count > 0) {
                    Toast.makeText(this@CachedSongsActivity, "已恢复 $count 首歌曲", Toast.LENGTH_SHORT).show()
                    loadCachedSongs()
                } else {
                    Toast.makeText(this@CachedSongsActivity, "恢复失败或没有可恢复的歌曲", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scanAndRestore() {
        lifecycleScope.launch {
            val count = cacheManager.scanAndRestoreBackups()
            runOnUiThread {
                if (count > 0) {
                    Toast.makeText(this@CachedSongsActivity, "已恢复 $count 首歌曲", Toast.LENGTH_SHORT).show()
                    loadCachedSongs()
                } else {
                    Toast.makeText(this@CachedSongsActivity, "未找到可恢复的备份", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun rescanAndComplete() {
        lifecycleScope.launch {
            // First restore from disk
            val restoredCount = cacheManager.restoreCachedFilesFromDisk()
            
            // Then complete missing info
            val completeResult = cacheManager.completeMissingCacheInfo { current, total ->
                runOnUiThread {
                    tvEmpty.text = "正在补全缓存信息... ($current/$total)"
                    tvEmpty.visibility = View.VISIBLE
                    rvSongs.visibility = View.GONE
                }
            }
            
            runOnUiThread {
                if (restoredCount > 0 || completeResult > 0) {
                    Toast.makeText(this@CachedSongsActivity, "补全完成！", Toast.LENGTH_SHORT).show()
                    loadCachedSongs()
                } else {
                    Toast.makeText(this@CachedSongsActivity, "已是最新状态", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCachedSongs()
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position >= 0 && position < currentSongs.size) {
                    val song = currentSongs[position]
                    AlertDialog.Builder(this@CachedSongsActivity)
                        .setTitle("删除缓存")
                        .setMessage("确定要删除「${song.name}」的缓存吗？")
                        .setPositiveButton("确定") { _, _ ->
                            lifecycleScope.launch {
                                cacheManager.removeCache(song.id)
                                loadCachedSongs()
                            }
                        }
                        .setNegativeButton("取消") { _, _ ->
                            songAdapter.notifyItemChanged(position)
                        }
                        .setOnCancelListener {
                            songAdapter.notifyItemChanged(position)
                        }
                        .show()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvSongs)
    }

    private fun loadCachedSongs() {
        lifecycleScope.launch {
            cacheManager.getCachedSongs().collect { cacheEntities ->
                if (cacheEntities.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                    val songs = cacheEntities.map { cache ->
                        Song(
                            id = cache.songId,
                            name = cache.songName,
                            artist = cache.artist,
                            album = cache.album,
                            pic = cache.pic.ifEmpty { "" },
                            lrc = cache.lrc.ifEmpty { "" },
                            url = cache.filePath,
                            isCached = true
                        )
                    }
                    currentSongs.clear()
                    currentSongs.addAll(songs)
                    songAdapter.submitList(songs)
                    songAdapter.setCached(cacheEntities.map { it.songId }.toSet())
                }
            }
        }
    }

    private fun showEmptyState() {
        tvEmpty.text = "暂无缓存歌曲"
        tvEmpty.visibility = View.VISIBLE
        rvSongs.visibility = View.GONE
    }

    private fun hideEmptyState() {
        tvEmpty.visibility = View.GONE
        rvSongs.visibility = View.VISIBLE
    }

    private fun playSong(song: Song, position: Int) {
        SongListHolder.songs = currentSongs.toList()
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = com.melodyflow.app.service.MusicService.ACTION_PLAY_SONG
            putExtra("song", song)
            putExtra("position", position)
        }
        startService(serviceIntent)
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("song", song)
            putExtra("clickIndex", position)
        })
    }

    private fun toggleFavorite(song: Song, isFavorite: Boolean) {
        lifecycleScope.launch {
            if (isFavorite) {
                repository.removeFavorite(song.id)
            } else {
                repository.addFavorite(song)
            }
        }
    }
}
