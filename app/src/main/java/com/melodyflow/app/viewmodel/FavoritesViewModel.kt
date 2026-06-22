package com.melodyflow.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.model.Song
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FavoritesState(
    val favorites: List<Song> = emptyList(),
    val cachedSongIds: Set<String> = emptySet(),
    val selectedIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MelodyFlowApp).repository
    private val cacheManager = CacheManager.getInstance(application)

    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state.asStateFlow()

    init {
        loadFavorites()
        loadCachedIds()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            repository.getFavorites().collect { list ->
                _state.update {
                    it.copy(favorites = list.map { entity ->
                        Song(
                            id = entity.id,
                            name = entity.name,
                            artist = entity.artist,
                            album = entity.album,
                            pic = entity.pic,
                            url = entity.url
                        )
                    })
                }
            }
        }
    }

    private fun loadCachedIds() {
        viewModelScope.launch {
            cacheManager.getCachedSongs().collect { list ->
                _state.update { it.copy(cachedSongIds = list.map { it.songId }.toSet()) }
            }
        }
    }

    fun toggleSelect(songId: String) {
        val current = _state.value.selectedIds
        val newSelected = if (current.contains(songId)) current - songId else current + songId
        _state.update { it.copy(selectedIds = newSelected, isMultiSelectMode = newSelected.isNotEmpty()) }
    }

    fun exitMultiSelect() {
        _state.update { it.copy(selectedIds = emptySet(), isMultiSelectMode = false) }
    }

    fun cacheSelected() {
        val state = _state.value
        viewModelScope.launch {
            state.favorites.filter { it.id in state.selectedIds }.forEach { song ->
                cacheManager.cacheSong(song)
            }
            exitMultiSelect()
        }
    }
}
