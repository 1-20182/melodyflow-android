package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private lateinit var tvEmpty: TextView
    
    private val currentSongs = mutableListOf<Song>()

    private val repository by lazy { (application as MelodyFlowApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_list)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.library_history)
        toolbar.inflateMenu(R.menu.menu_history)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear_history -> {
                    showClearHistoryDialog()
                    true
                }
                else -> false
            }
        }

        rvSongs = findViewById(R.id.rvSongs)
        rvSongs.layoutManager = LinearLayoutManager(this)

        // Setup empty state views
        tvEmpty = findViewById(R.id.tvEmpty)

        songAdapter = SongAdapter(
            onItemClick = { song, position -> playSong(song, position) },
            onFavoriteClick = { song, fav -> toggleFavorite(song, fav) }
        )
        rvSongs.adapter = songAdapter

        loadHistory()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空播放记录")
            .setMessage("确定要清空所有播放记录吗？此操作不可撤销。")
            .setPositiveButton("清空") { _, _ ->
                lifecycleScope.launch {
                    repository.clearHistory()
                    currentSongs.clear()
                    songAdapter.submitList(emptyList())
                    showEmptyState()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            repository.getHistory().collect { history ->
                if (history.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                    val songs = history.map {
                        Song(id = it.id, name = it.name, artist = it.artist, album = it.album, pic = it.pic, url = it.url)
                    }
                    currentSongs.clear()
                    currentSongs.addAll(songs)
                    songAdapter.submitList(songs)
                }
            }
        }
    }

    private fun showEmptyState() {
        tvEmpty.text = "暂无播放记录"
        tvEmpty.visibility = View.VISIBLE
        rvSongs.visibility = View.GONE
    }

    private fun hideEmptyState() {
        tvEmpty.visibility = View.GONE
        rvSongs.visibility = View.VISIBLE
    }

    private fun playSong(song: Song, position: Int) {
        (application as MelodyFlowApp).repository.setPendingPlaylist(currentSongs.toList())
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_SONG
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
            if (isFavorite) repository.addFavorite(song) else repository.removeFavorite(song.id)
        }
    }
}
