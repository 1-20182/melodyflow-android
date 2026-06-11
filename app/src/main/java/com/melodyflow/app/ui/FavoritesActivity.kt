package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.SongListHolder
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.launch

class FavoritesActivity : AppCompatActivity() {

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
        toolbar.title = getString(R.string.library_favorites)

        rvSongs = findViewById(R.id.rvSongs)
        rvSongs.layoutManager = LinearLayoutManager(this)

        // Setup empty state views
        tvEmpty = findViewById(R.id.tvEmpty)

        songAdapter = SongAdapter(
            onItemClick = { song, position -> playSong(song, position) },
            onFavoriteClick = { song, fav -> toggleFavorite(song, fav) }
        )
        rvSongs.adapter = songAdapter

        // Setup swipe-to-remove from favorites
        setupSwipeToDelete()

        loadFavorites()
    }

    override fun onResume() {
        super.onResume()
        songAdapter.refreshUnplayable()
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

    private fun loadFavorites() {
        lifecycleScope.launch {
            repository.getFavorites().collect { favorites ->
                if (favorites.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                    val songs = favorites.map {
                        android.util.Log.d("FavoritesActivity", "Loading favorite: id=${it.id}, name=${it.name}, pic=${it.pic}")
                        Song(id = it.id, name = it.name, artist = it.artist, album = it.album, pic = it.pic, url = it.url)
                    }
                    currentSongs.clear()
                    currentSongs.addAll(songs)
                    songAdapter.submitList(songs)
                    songAdapter.setFavorites(favorites.map { it.id }.toSet())
                }
            }
        }
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
        SongListHolder.songs = currentSongs.toList()
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
