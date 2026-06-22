package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import com.melodyflow.app.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

class FavoritesTabFragment : Fragment() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var songAdapter: SongAdapter
    private val currentSongs = mutableListOf<Song>()

    private val repository by lazy {
        (requireActivity().application as MelodyFlowApp).repository
    }

    private val viewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_favorites_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvSongs = view.findViewById(R.id.rvFavorites)
        tvEmpty = view.findViewById(R.id.tvEmptyFavorites)

        songAdapter = SongAdapter(
            onItemClick = { song, position -> playSong(song, position) },
            onFavoriteClick = { song, fav -> toggleFavorite(song, fav) }
        )
        rvSongs.adapter = songAdapter
        rvSongs.layoutManager = LinearLayoutManager(requireContext())

        setupSwipeToDelete()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        songAdapter.refreshUnplayable()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
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
                val position = viewHolder.bindingAdapterPosition
                if (position >= 0 && position < currentSongs.size) {
                    val song = currentSongs[position]
                    AlertDialog.Builder(requireContext())
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
        tvEmpty.visibility = View.VISIBLE
        rvSongs.visibility = View.GONE
    }

    private fun hideEmptyState() {
        tvEmpty.visibility = View.GONE
        rvSongs.visibility = View.VISIBLE
    }

    private fun playSong(song: Song, position: Int) {
        (requireActivity().application as MelodyFlowApp).repository.setPendingPlaylist(currentSongs.toList())
        val serviceIntent = Intent(requireContext(), MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_SONG
            putExtra("song", song)
            putExtra("position", position)
        }
        requireContext().startService(serviceIntent)
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
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
