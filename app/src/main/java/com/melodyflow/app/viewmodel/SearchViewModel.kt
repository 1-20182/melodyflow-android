package com.melodyflow.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.data.ApiResult
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.model.Song
import com.melodyflow.app.util.SearchHistoryManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val cachedSongIds: Set<String> = emptySet(),
    val downloadingIds: Set<String> = emptySet()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MelodyFlowApp).repository
    private val cacheManager = CacheManager.getInstance(application)
    private val historyManager = SearchHistoryManager(application)

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        loadHistory()
        loadCachedIds()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(query = query, isLoading = true) }
        historyManager.addQuery(query)

        viewModelScope.launch {
            try {
                val result = repository.search(query)
                val songs = if (result is ApiResult.Success) result.data else emptyList()
                _state.update { it.copy(results = songs, isLoading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun loadHistory() {
        _state.update { it.copy(searchHistory = historyManager.getHistory()) }
    }

    fun removeHistoryItem(query: String) {
        historyManager.removeQuery(query)
        loadHistory()
    }

    fun clearHistory() {
        historyManager.clearHistory()
        loadHistory()
    }

    private fun loadCachedIds() {
        viewModelScope.launch {
            cacheManager.getCachedSongs().collect { list ->
                _state.update { it.copy(cachedSongIds = list.map { e -> e.songId }.toSet()) }
            }
        }
    }
}
