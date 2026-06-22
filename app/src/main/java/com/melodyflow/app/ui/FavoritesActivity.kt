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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.data.SilentCacheManager
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import com.melodyflow.app.viewmodel.FavoritesViewModel
import kotlinx.coroutines.launch

class FavoritesActivity : AppCompatActivity() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var toolbar: MaterialToolbar
    private val currentSongs = mutableListOf<Song>()

    private val repository by lazy { (application as MelodyFlowApp).repository }
    private lateinit var viewModel: FavoritesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_list)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        viewModel = ViewModelProvider(this).get(FavoritesViewModel::class.java)

        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            if (viewModel.state.value.isMultiSelectMode) {
                viewModel.exitMultiSelect()
            } else {
                finish()
            }
        }
        toolbar.title = getString(R.string.library_favorites)

        rvSongs = findViewById(R.id.rvSongs)
        rvSongs.layoutManager = LinearLayoutManager(this)

        tvEmpty = findViewById(R.id.tvEmpty)

        songAdapter = SongAdapter(
            onItemClick = { song, position ->
                if (viewModel.state.value.isMultiSelectMode) {
                    viewModel.toggleSelect(song.id)
                } else {
                    playSong(song, position)
                }
            },
            onFavoriteClick = { song, fav -> toggleFavorite(song, fav) },
            onCacheClick = { song -> cacheSong(song) },
            showCacheIndicator = true
        )
        rvSongs.adapter = songAdapter

        // Long press enters multi-select mode
        songAdapter.setOnLongClickListener { song ->
            if (!viewModel.state.value.isMultiSelectMode) {
                // enter multi-select is handled by toggleSelect
            }
            viewModel.toggleSelect(song.id)
        }

        setupSwipeToDelete()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        songAdapter.refreshUnplayable()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (viewModel.state.value.isMultiSelectMode) {
            menuInflater.inflate(R.menu.menu_favorites_multi_select, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cache_selected -> {
                cacheSelectedSongs()
                true
            }
            R.id.action_select_all -> {
                selectAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (viewModel.state.value.isMultiSelectMode) {
            viewModel.exitMultiSelect()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun selectAll() {
        currentSongs.forEach { song ->
            if (song.id !in viewModel.state.value.selectedIds) {
                viewModel.toggleSelect(song.id)
            }
        }
    }

    private fun cacheSelectedSongs() {
        val state = viewModel.state.value
        val songsToCache = currentSongs.filter { it.id in state.selectedIds }
        if (songsToCache.isEmpty()) return

        lifecycleScope.launch {
            Toast.makeText(this@FavoritesActivity, "正在缓存 ${songsToCache.size} 首歌曲...", Toast.LENGTH_SHORT).show()
            songsToCache.forEach { song ->
                try {
                    SilentCacheManager.cacheSong(
                        context = this@FavoritesActivity,
                        song = song,
                        rootView = findViewById(android.R.id.content)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            Toast.makeText(this@FavoritesActivity, "缓存任务已提交", Toast.LENGTH_SHORT).show()
            viewModel.exitMultiSelect()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Update favorites list
                    val songs = state.favorites
                    if (songs.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        currentSongs.clear()
                        currentSongs.addAll(songs)
                        songAdapter.submitList(songs)
                        songAdapter.setFavorites(songs.map { it.id }.toSet())
                    }

                    // Update cache status
                    songAdapter.setCached(state.cachedSongIds)

                    // Update multi-select UI
                    if (state.isMultiSelectMode) {
                        toolbar.title = "已选择 ${state.selectedIds.size} 首"
                        songAdapter.setSelectedIds(state.selectedIds)
                    } else {
                        toolbar.title = getString(R.string.library_favorites)
                        songAdapter.setSelectedIds(emptySet())
                    }
                    invalidateOptionsMenu()
                }
            }
        }
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
                if (viewModel.state.value.isMultiSelectMode) {
                    songAdapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                    return
                }
                val position = viewHolder.bindingAdapterPosition
                if (position >= 0 && position < currentSongs.size) {
                    val song = currentSongs[position]
                    AlertDialog.Builder(this@FavoritesActivity)
                        .setTitle("取消收藏")
                        .setMessage("确定要取消收藏「${song.name}」吗？")
                        .setPositiveButton("确定") { _, _ ->
                            lifecycleScope.launch {
                                repository.removeFavorite(song.id)
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

    private fun showEmptyState() {
        tvEmpty.text = "暂无收藏"
        tvEmpty.visibility = View.VISIBLE
        rvSongs.visibility = View.GONE
    }

    private fun hideEmptyState() {
        tvEmpty.visibility = View.GONE
        rvSongs.visibility = View.VISIBLE
    }

    private fun playSong(song: Song, position: Int) {
        android.util.Log.d("FavoritesActivity", "playSong: id=${song.id}, name=${song.name}, pic=${song.pic}, coverUrl=${song.getCoverUrl()}")
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

    private fun cacheSong(song: Song) {
        lifecycleScope.launch {
            try {
                SilentCacheManager.cacheSong(
                    context = this@FavoritesActivity,
                    song = song,
                    rootView = findViewById(android.R.id.content)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
