package com.melodyflow.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.model.Song
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryState(
    val favorites: List<Song> = emptyList(),
    val recentHistory: List<Song> = emptyList(),
    val localSongs: List<Song> = emptyList(),
    val isScanning: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MelodyFlowApp).repository

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        loadFavorites()
        loadHistory()
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

    private fun loadHistory() {
        viewModelScope.launch {
            repository.getHistory().collect { list ->
                _state.update {
                    it.copy(recentHistory = list.map { entity ->
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

    fun updateLocalSongs(songs: List<Song>) {
        _state.update { it.copy(localSongs = songs) }
    }

    fun setScanning(scanning: Boolean) {
        _state.update { it.copy(isScanning = scanning) }
    }
}
