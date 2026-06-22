package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.LocalSongAdapter
import com.melodyflow.app.data.LocalScanService
import com.melodyflow.app.db.LocalSongEntity
import com.melodyflow.app.db.MusicDatabase
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocalSongsFragment : Fragment() {

    private lateinit var rvSongs: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: LocalSongAdapter

    private val localSongDao by lazy {
        MusicDatabase.getInstance(requireContext()).localSongDao()
    }

    private val scanService by lazy {
        LocalScanService.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_local_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvSongs = view.findViewById(R.id.rvLocalSongs)
        tvEmpty = view.findViewById(R.id.tvEmptyLocal)

        adapter = LocalSongAdapter { song, position ->
            playLocalSong(song, position)
        }
        rvSongs.adapter = adapter
        rvSongs.layoutManager = LinearLayoutManager(requireContext())

        loadLocalSongs()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun loadLocalSongs() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                localSongDao.getAll().collect { songs ->
                    if (songs.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        rvSongs.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        rvSongs.visibility = View.VISIBLE
                        adapter.submitList(songs)
                    }
                }
            }
        }
    }

    private fun playLocalSong(entity: LocalSongEntity, position: Int) {
        // Convert LocalSongEntity to Song for playback
        val song = Song(
            id = "local_${entity.filePath.hashCode()}",
            name = entity.title,
            artist = entity.artist,
            album = entity.album,
            pic = "",
            url = entity.filePath  // Local file path as URL
        )

        // Build playlist from all local songs
        val currentList = adapter.currentList
        val playlist = currentList.map { localEntity ->
            Song(
                id = "local_${localEntity.filePath.hashCode()}",
                name = localEntity.title,
                artist = localEntity.artist,
                album = localEntity.album,
                pic = "",
                url = localEntity.filePath
            )
        }

        (requireActivity().application as MelodyFlowApp).repository.setPendingPlaylist(playlist)
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

    fun startScan() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "正在扫描本地音乐...", Toast.LENGTH_SHORT).show()
                val count = scanService.fullScan()
                if (count > 0) {
                    Toast.makeText(requireContext(), "发现 $count 首新歌曲", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "未发现新的本地歌曲", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
