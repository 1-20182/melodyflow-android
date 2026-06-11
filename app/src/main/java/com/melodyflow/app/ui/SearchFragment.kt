package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.data.SilentCacheManager
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.SongListHolder
import com.melodyflow.app.service.MusicService
import com.melodyflow.app.util.SearchHistoryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var songAdapter: SongAdapter
    private lateinit var tvSearchHistory: TextView
    private lateinit var rvSearchHistory: RecyclerView
    private lateinit var rvResults: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyManager: SearchHistoryManager

    private var currentSongResults = listOf<Song>()
    private val downloadingSongIds = mutableSetOf<String>()

    private val repository by lazy {
        (requireActivity().application as MelodyFlowApp).repository
    }

    private val cacheManager by lazy {
        CacheManager.getInstance(requireContext())
    }

    private var searchDebounceJob: Job? = null
    private val SEARCH_DEBOUNCE_MS = 300L

    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyManager = SearchHistoryManager(requireContext())

        etSearch = view.findViewById(R.id.etSearch)
        progressBar = view.findViewById(R.id.progressBar)
        tvSearchHistory = view.findViewById(R.id.tvSearchHistory)
        rvSearchHistory = view.findViewById(R.id.rvSearchHistory)
        rvResults = view.findViewById(R.id.rvResults)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        songAdapter = SongAdapter(
            onItemClick = { song, position ->
                playSong(song, position)
            },
            onFavoriteClick = { song, isFavorite ->
                toggleFavorite(song, isFavorite)
            },
            onCacheClick = { song ->
                cacheSong(song)
            }
        )

        historyAdapter = HistoryAdapter({ query ->
            etSearch.setText(query)
            etSearch.setSelection(query.length)
            performSearch()
        }, { historyManager.clearHistory(); loadSearchHistory() })
        rvSearchHistory.adapter = historyAdapter

        rvResults.adapter = songAdapter
        rvResults.layoutManager = LinearLayoutManager(requireContext())

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        etSearch.setOnKeyListener { _, _, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    val query = etSearch.text.toString().trim()
                    if (query.isNotEmpty() && query != currentQuery) {
                        performSearch()
                    }
                }
            }
            false
        }

        loadSearchHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshCacheState()
    }

    private fun loadSearchHistory() {
        val history = historyManager.getHistory()
        rvResults.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        progressBar.visibility = View.GONE

        if (history.isNotEmpty()) {
            tvSearchHistory.visibility = View.VISIBLE
            rvSearchHistory.visibility = View.VISIBLE
            historyAdapter.submitList(history)
        } else {
            tvSearchHistory.visibility = View.GONE
            rvSearchHistory.visibility = View.GONE
        }
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) return

        searchDebounceJob?.cancel()
        historyManager.addQuery(query)
        currentQuery = query

        progressBar.visibility = View.VISIBLE
        tvSearchHistory.visibility = View.GONE
        rvSearchHistory.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = repository.search(query)
                val songList = if (results is com.melodyflow.app.data.ApiResult.Success) results.data else emptyList()
                currentSongResults = songList
                songAdapter.submitList(songList)
                progressBar.visibility = View.GONE
                
                if (songList.isEmpty()) {
                    tvEmpty.text = "未找到相关歌曲"
                    tvEmpty.visibility = View.VISIBLE
                    rvResults.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvResults.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvEmpty.text = getString(R.string.error_network)
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun playSong(song: Song, position: Int) {
        SongListHolder.songs = currentSongResults
        val serviceIntent = Intent(requireContext(), MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_SONG
            putExtra("song", song)
            putExtra("position", position)
        }
        requireContext().startService(serviceIntent)
        
        // Open player activity to show playback interface
        val playerIntent = Intent(requireContext(), PlayerActivity::class.java).apply {
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
                SilentCacheManager.cacheSong(
                    context = requireContext(),
                    song = song,
                    rootView = requireView()
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

    override fun onDestroyView() {
        super.onDestroyView()
        cachedSongsJob?.cancel()
    }
}

class HistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onClearClick: () -> Unit
) : androidx.recyclerview.widget.ListAdapter<String, HistoryAdapter.HistoryViewHolder>(object : androidx.recyclerview.widget.DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
}) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemCount(): Int = currentList.size

    inner class HistoryViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val tvQuery: TextView = itemView.findViewById(R.id.tvQuery)
        private val btnRemove: View = itemView.findViewById(R.id.btnRemoveHistory)

        fun bind(query: String) {
            tvQuery.text = query
            itemView.setOnClickListener { onItemClick(query) }
            btnRemove.setOnClickListener { onClearClick() }
        }
    }
}