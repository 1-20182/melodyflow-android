package com.melodyflow.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.LyricLine
import com.melodyflow.app.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0,
    val duration: Long = 0,
    val playMode: MusicService.PlayMode = MusicService.PlayMode.SEQUENCE,
    val isFavorite: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MelodyFlowApp).repository

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var favoriteCheckJob: Job? = null

    fun updateCurrentSong(song: Song?) {
        _state.update { it.copy(currentSong = song, isFavorite = false) }
        favoriteCheckJob?.cancel()
        song?.let { checkFavorite(it.id) }
    }

    fun updatePlayState(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
    }

    fun updateProgress(progress: Long, duration: Long) {
        _state.update { it.copy(progress = progress, duration = duration) }
    }

    fun updatePlayMode(mode: MusicService.PlayMode) {
        _state.update { it.copy(playMode = mode) }
    }

    fun updateLyrics(lyrics: List<LyricLine>) {
        _state.update { it.copy(lyrics = lyrics) }
    }

    fun updateLyricIndex(index: Int) {
        _state.update { it.copy(currentLyricIndex = index) }
    }

    private fun checkFavorite(songId: String) {
        favoriteCheckJob = viewModelScope.launch {
            repository.isFavorite(songId).collect { fav ->
                _state.update { it.copy(isFavorite = fav) }
            }
        }
    }

    fun toggleFavorite() {
        val song = _state.value.currentSong ?: return
        viewModelScope.launch {
            val isFav = repository.isFavoriteSync(song.id)
            if (isFav) {
                repository.removeFavorite(song.id)
            } else {
                repository.addFavorite(song)
            }
            _state.update { it.copy(isFavorite = !isFav) }
        }
    }
}
