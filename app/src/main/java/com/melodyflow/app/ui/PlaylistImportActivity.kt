package com.melodyflow.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import androidx.viewpager2.widget.ViewPager2
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.ImportResultAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaylistImportActivity : AppCompatActivity() {

    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_import)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        toolbar.setNavigationOnClickListener { finish() }

        viewPager.adapter = ImportPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.import_tab_url)
                1 -> getString(R.string.import_tab_text)
                else -> ""
            }
        }.attach()
    }

    class ImportPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> UrlImportFragment()
                1 -> TextImportFragment()
                else -> UrlImportFragment()
            }
        }
    }
}

class UrlImportFragment : Fragment() {

    private lateinit var etUrl: TextInputEditText
    private lateinit var btnParse: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var rvResults: RecyclerView
    private lateinit var songAdapter: ImportResultAdapter

    private val repository by lazy {
        (requireActivity().application as MelodyFlowApp).repository
    }
    private var musicService: MusicService? = null
    private var serviceBound = false
    private var currentSongs = listOf<Song>()
    private var isParsing = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_import_url, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etUrl = view.findViewById(R.id.etPlaylistUrl)
        btnParse = view.findViewById(R.id.btnParseUrl)
        progressBar = view.findViewById(R.id.progressBarUrl)
        tvEmpty = view.findViewById(R.id.tvUrlEmpty)
        rvResults = view.findViewById(R.id.rvUrlResults)

        songAdapter = ImportResultAdapter(
            onPlay = { playSong(it) },
            onFavorite = { song, fav -> toggleFavorite(song, fav) }
        )

        rvResults.adapter = songAdapter
        rvResults.layoutManager = LinearLayoutManager(requireContext()).apply {
            isItemPrefetchEnabled = true
            initialPrefetchItemCount = 10
        }
        rvResults.setHasFixedSize(true)
        rvResults.setItemViewCacheSize(20)

        btnParse.setOnClickListener { parsePlaylist() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun parsePlaylist() {
        if (isParsing) return

        val url = etUrl.text?.toString()?.trim() ?: return
        val playlistId = repository.parsePlaylistUrl(url)
        if (playlistId == null) {
            etUrl.error = getString(R.string.import_invalid_url)
            return
        }

        isParsing = true
        etUrl.error = null
        btnParse.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvResults.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val songs = repository.getPlaylistSongs(playlistId)
                progressBar.visibility = View.GONE
                btnParse.isEnabled = true
                isParsing = false

                if (songs.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    currentSongs = songs
                    submitSongsInBatches(songs)
                    rvResults.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnParse.isEnabled = true
                isParsing = false
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = getString(R.string.error_network)
            }
        }
    }

    private fun submitSongsInBatches(songs: List<Song>) {
        val batchSize = 20
        val totalBatches = (songs.size + batchSize - 1) / batchSize

        lifecycleScope.launch {
            for (batchIndex in 0 until totalBatches) {
                val start = batchIndex * batchSize
                val end = minOf(start + batchSize, songs.size)
                val batch = songs.subList(start, end)

                songAdapter.submitList(if (batchIndex == 0) batch.toList() else songAdapter.currentList + batch)

                if (batchIndex < totalBatches - 1) {
                    delay(50)
                }
            }
        }
    }

    private fun playSong(song: Song) {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(intent)

        if (!serviceBound) {
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        musicService?.let { service ->
            service.playSong(song)
        } ?: run {
            val playerIntent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("song", song)
            }
            startActivity(playerIntent)
        }
    }

    private fun toggleFavorite(song: Song, isFavorite: Boolean) {
        lifecycleScope.launch {
            if (isFavorite) repository.addFavorite(song)
            else repository.removeFavorite(song.id)
        }
    }
}

class TextImportFragment : Fragment() {

    private lateinit var etNames: TextInputEditText
    private lateinit var btnSearch: MaterialButton
    private lateinit var layoutProgress: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvStats: TextView
    private lateinit var rvResults: RecyclerView
    private lateinit var songAdapter: ImportResultAdapter

    private val repository by lazy {
        (requireActivity().application as MelodyFlowApp).repository
    }
    private var musicService: MusicService? = null
    private var serviceBound = false
    private var isSearching = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicService.LocalBinder)?.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_import_text, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etNames = view.findViewById(R.id.etSongNames)
        btnSearch = view.findViewById(R.id.btnSearchImport)
        layoutProgress = view.findViewById(R.id.layoutProgress)
        progressBar = view.findViewById(R.id.progressBarText)
        tvProgress = view.findViewById(R.id.tvProgressText)
        tvStats = view.findViewById(R.id.tvStats)
        rvResults = view.findViewById(R.id.rvTextResults)

        songAdapter = ImportResultAdapter(
            onPlay = { playSong(it) },
            onFavorite = { song, fav -> toggleFavorite(song, fav) }
        )

        rvResults.adapter = songAdapter
        rvResults.layoutManager = LinearLayoutManager(requireContext()).apply {
            isItemPrefetchEnabled = true
            initialPrefetchItemCount = 10
        }
        rvResults.setHasFixedSize(true)
        rvResults.setItemViewCacheSize(20)

        btnSearch.setOnClickListener { batchSearch() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun batchSearch() {
        if (isSearching) return

        val text = etNames.text?.toString()?.trim() ?: return
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        isSearching = true
        btnSearch.isEnabled = false
        layoutProgress.visibility = View.VISIBLE
        tvStats.visibility = View.GONE
        rvResults.visibility = View.GONE

        lifecycleScope.launch {
            val results = repository.batchSearchAndFavorite(lines) { current, total ->
                tvProgress.text = getString(R.string.import_searching, current, total)
            }

            layoutProgress.visibility = View.GONE
            btnSearch.isEnabled = true
            isSearching = false

            val successSongs = results.mapNotNull { it.second }
            val failCount = results.count { it.second == null }

            if (successSongs.isNotEmpty()) {
                submitSongsInBatches(successSongs)
                rvResults.visibility = View.VISIBLE
            }

            tvStats.text = getString(R.string.import_success, successSongs.size) +
                    if (failCount > 0) " · " + getString(R.string.import_failed, failCount) else ""
            tvStats.visibility = View.VISIBLE
        }
    }

    private fun submitSongsInBatches(songs: List<Song>) {
        val batchSize = 20
        val totalBatches = (songs.size + batchSize - 1) / batchSize

        lifecycleScope.launch {
            for (batchIndex in 0 until totalBatches) {
                val start = batchIndex * batchSize
                val end = minOf(start + batchSize, songs.size)
                val batch = songs.subList(start, end)

                songAdapter.submitList(if (batchIndex == 0) batch.toList() else songAdapter.currentList + batch)

                if (batchIndex < totalBatches - 1) {
                    delay(50)
                }
            }
        }
    }

    private fun playSong(song: Song) {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().startService(intent)

        if (!serviceBound) {
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        musicService?.let { service ->
            service.playSong(song)
        } ?: run {
            val playerIntent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("song", song)
            }
            startActivity(playerIntent)
        }
    }

    private fun toggleFavorite(song: Song, isFavorite: Boolean) {
        lifecycleScope.launch {
            if (isFavorite) repository.addFavorite(song)
            else repository.removeFavorite(song.id)
        }
    }
}