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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.adapter.SongAdapter
import com.melodyflow.app.data.SilentCacheManager
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService
import com.melodyflow.app.viewmodel.SearchViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var songAdapter: SongAdapter
    private lateinit var tvSearchHistory: TextView
    private lateinit var rvSearchHistory: RecyclerView
    private lateinit var rvResults: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    private var currentSongResults = listOf<Song>()
    private val downloadingSongIds = mutableSetOf<String>()

    private val repository by lazy {
        (requireActivity().application as MelodyFlowApp).repository
    }

    private val viewModel: SearchViewModel by viewModels()

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

        searchBar = view.findViewById(R.id.searchBar)
        searchView = view.findViewById(R.id.searchView)
        progressBar = view.findViewById(R.id.progressBar)
        tvSearchHistory = view.findViewById(R.id.tvSearchHistory)
        rvSearchHistory = view.findViewById(R.id.rvSearchHistory)
        rvResults = view.findViewById(R.id.rvResults)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        // Connect SearchBar to SearchView (MD3 standard pattern)
        searchView.setupWithSearchBar(searchBar)

        songAdapter = SongAdapter(
            onItemClick = { song, position ->
                playSong(song, position)
            },
            onFavoriteClick = { song, isFavorite ->
                toggleFavorite(song, isFavorite)
            },
            onCacheClick = { song ->
                cacheSong(song)
            },
            showCacheIndicator = true
        )

        historyAdapter = HistoryAdapter(
            onItemClick = { query ->
                searchView.editText.setText(query)
                searchView.editText.setSelection(query.length)
                performSearch(query)
            },
            onItemRemoveClick = { query ->
                viewModel.removeHistoryItem(query)
            },
            onClearAllClick = {
                viewModel.clearHistory()
            }
        )
        rvSearchHistory.adapter = historyAdapter

        rvResults.adapter = songAdapter
        rvResults.layoutManager = LinearLayoutManager(requireContext())

        // Handle search text changes with debounce and submit via SearchView's EditText
        val searchEditText: EditText = searchView.editText
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        searchEditText.setOnKeyListener { _, _, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    val query = searchEditText.text.toString().trim()
                    if (query.isNotEmpty() && query != currentQuery) {
                        performSearchInternal(query)
                    }
                }
            }
            false
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Refresh cache state when returning to fragment
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Update search history (inside SearchView)
                    val history = state.searchHistory
                    historyAdapter.submitList(history)
                    tvSearchHistory.visibility = if (history.isNotEmpty()) View.VISIBLE else View.GONE
                    rvSearchHistory.visibility = if (history.isNotEmpty()) View.VISIBLE else View.GONE

                    // Update search results (main content area)
                    if (state.isLoading) {
                        progressBar.visibility = View.VISIBLE
                        tvEmpty.visibility = View.GONE
                        rvResults.visibility = View.GONE
                    } else if (state.results.isNotEmpty()) {
                        progressBar.visibility = View.GONE
                        currentSongResults = state.results
                        songAdapter.submitList(state.results)
                        tvEmpty.visibility = View.GONE
                        rvResults.visibility = View.VISIBLE
                    } else if (state.query.isNotEmpty()) {
                        progressBar.visibility = View.GONE
                        currentSongResults = state.results
                        songAdapter.submitList(state.results)
                        if (state.error != null) {
                            tvEmpty.text = getString(R.string.error_network)
                        } else {
                            tvEmpty.text = "未找到相关歌曲"
                        }
                        tvEmpty.visibility = View.VISIBLE
                        rvResults.visibility = View.GONE
                    } else {
                        progressBar.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        rvResults.visibility = View.GONE
                    }

                    // Update cached song IDs
                    songAdapter.setCached(state.cachedSongIds)
                }
            }
        }
    }

    /**
     * Perform search without collapsing the SearchView.
     * Used by the debounce listener for real-time search while typing.
     */
    private fun performSearchInternal(query: String) {
        if (query.isEmpty()) return
        searchDebounceJob?.cancel()
        currentQuery = query
        viewModel.search(query)
    }

    /**
     * Perform search and collapse the SearchView to show results.
     * Used when the user explicitly submits a search or taps a history item.
     */
    private fun performSearch(query: String = searchView.editText.text.toString().trim()) {
        if (query.isEmpty()) return
        searchDebounceJob?.cancel()
        currentQuery = query
        viewModel.search(query)
        searchView.hide()
    }

    private fun playSong(song: Song, position: Int) {
        (requireActivity().application as MelodyFlowApp).repository.setPendingPlaylist(currentSongResults)
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
            }
        }
    }
}

class HistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemRemoveClick: (String) -> Unit,
    private val onClearAllClick: () -> Unit
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
            btnRemove.setOnClickListener { onItemRemoveClick(query) }
        }
    }
}
