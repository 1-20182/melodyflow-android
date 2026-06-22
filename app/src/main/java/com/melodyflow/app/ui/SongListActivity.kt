package com.melodyflow.app.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.data.SilentCacheManager
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.launch

class SongListActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var songAdapter: SongAdapter
    private val currentSongs = mutableListOf<Song>()

    private val downloadingSongIds = mutableSetOf<String>()

    private val repository by lazy {
        (application as MelodyFlowApp).repository
    }

    private val cacheManager by lazy {
        CacheManager.getInstance(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SongListActivity", "onCreate called")
        // Use proper activity_song_list layout instead of reusing fragment_search
        setContentView(R.layout.activity_song_list)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        val chartName = intent.getStringExtra("chart_name") ?: ""
        val chartId = intent.getStringExtra("chart_id") ?: ""
        val chartServer = intent.getStringExtra("chart_server") ?: ""

        // Set toolbar title
        try {
            val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
            toolbar.title = chartName.ifEmpty { "歌曲列表" }
            toolbar.setNavigationOnClickListener { finish() }
        } catch (e: Exception) {
            // Toolbar may not exist in layout
        }

        rvSongs = findViewById(R.id.rvSongs)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        // Use two-column layout in landscape
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rvSongs.layoutManager = if (isLandscape) {
            GridLayoutManager(this, 2)
        } else {
            LinearLayoutManager(this)
        }

        songAdapter = SongAdapter(
            onItemClick = { song, position -> playSong(song, position) },
            onFavoriteClick = { song, isFavorite ->
                toggleFavorite(song, isFavorite)
            },
            onCacheClick = { song ->
                cacheSong(song)
            }
        )
        rvSongs.adapter = songAdapter

        loadChartSongs(chartServer, chartId)
    }

    override fun onResume() {
        super.onResume()
        refreshCacheState()
    }

    private fun loadChartSongs(server: String, id: String) {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvSongs.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val songs = repository.getChart(server, id)
                progressBar.visibility = View.GONE

                if (songs.isEmpty()) {
                    showEmptyState("暂无歌曲数据")
                } else {
                    currentSongs.clear()
                    currentSongs.addAll(songs)
                    songAdapter.submitList(songs)
                    rvSongs.visibility = View.VISIBLE
                    tvEmpty.visibility = View.GONE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showEmptyState(getString(R.string.error_network))
            }
        }
    }

    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        rvSongs.visibility = View.GONE
    }

    private fun playSong(song: Song, position: Int) {
        (application as MelodyFlowApp).repository.setPendingPlaylist(currentSongs.toList())
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_SONG
            putExtra("song", song)
            putExtra("position", position)
        }
        startService(serviceIntent)
        val playerIntent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("song", song)
            putExtra("clickIndex", position)
        }
        startActivity(playerIntent)
    }

    private fun toggleFavorite(song: Song, isFavorite: Boolean) {
        lifecycleScope.launch {
            if (isFavorite) {
                repository.addFavorite(song)
            } else {
                repository.removeFavorite(song.id)
            }
        }
    }

    private fun cacheSong(song: Song) {
        downloadingSongIds.add(song.id)
        songAdapter.setDownloading(downloadingSongIds.toSet())

        lifecycleScope.launch {
            try {
                // 使用静默缓存，不弹弹窗
                val rootView = findViewById<View>(android.R.id.content)
                SilentCacheManager.cacheSong(
                    context = this@SongListActivity,
                    song = song,
                    rootView = rootView
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                downloadingSongIds.remove(song.id)
                refreshCacheState()
            }
        }
    }

    private var cachedSongsJob: kotlinx.coroutines.Job? = null

    private fun refreshCacheState() {
        cachedSongsJob?.cancel()
        cachedSongsJob = lifecycleScope.launch {
            val cachedSongsFlow = cacheManager.getCachedSongs()
            cachedSongsFlow.collect { list ->
                val ids = list.map { it.songId }.toSet()
                songAdapter.setCached(ids)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cachedSongsJob?.cancel()
    }
}
