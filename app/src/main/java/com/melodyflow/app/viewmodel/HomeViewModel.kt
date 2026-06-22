package com.melodyflow.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.model.Chart
import com.melodyflow.app.model.Song
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeState(
    val charts: List<Chart> = emptyList(),
    val chartSongs: Map<String, List<Song>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MelodyFlowApp).repository

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun loadCharts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val charts = repository.getCharts()
                _state.update { it.copy(charts = charts, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun loadChartSongs(chartId: String) {
        viewModelScope.launch {
            try {
                val songs = repository.getPlaylist(chartId)
                _state.update {
                    it.copy(chartSongs = it.chartSongs + (chartId to songs))
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
