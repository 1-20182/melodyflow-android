package com.melodyflow.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.data.CacheManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsState(
    val cacheSize: Long = 0,
    val cacheCount: Int = 0,
    val cacheLimitMb: Int = 500,
    val isAutoBackupEnabled: Boolean = true,
    val scanDirectories: List<String> = emptyList(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportPath: String? = null,
    val importPreview: String? = null,
    val error: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MelodyFlowApp).repository
    private val cacheManager = CacheManager.getInstance(application)
    private val prefs = application.getSharedPreferences("melodyflow_settings", 0)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadCacheInfo()
        loadSettings()
    }

    fun loadCacheInfo() {
        viewModelScope.launch {
            val count = cacheManager.getCacheCount()
            val size = cacheManager.getCacheSize()
            _state.update { it.copy(cacheSize = size, cacheCount = count) }
        }
    }

    private fun loadSettings() {
        val limit = prefs.getInt("cache_limit_mb", 500)
        val autoBackup = prefs.getBoolean("auto_backup_enabled", true)
        val dirs = prefs.getStringSet("scan_directories", emptySet())?.toList() ?: emptyList()
        _state.update { it.copy(cacheLimitMb = limit, isAutoBackupEnabled = autoBackup, scanDirectories = dirs) }
    }

    fun setCacheLimitMb(limit: Int) {
        prefs.edit().putInt("cache_limit_mb", limit).apply()
        _state.update { it.copy(cacheLimitMb = limit) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        _state.update { it.copy(isAutoBackupEnabled = enabled) }
    }

    fun addScanDirectory(path: String) {
        val current = _state.value.scanDirectories.toMutableList()
        if (path !in current) {
            current.add(path)
            prefs.edit().putStringSet("scan_directories", current.toSet()).apply()
            _state.update { it.copy(scanDirectories = current) }
        }
    }

    fun removeScanDirectory(path: String) {
        val current = _state.value.scanDirectories.toMutableList()
        current.remove(path)
        prefs.edit().putStringSet("scan_directories", current.toSet()).apply()
        _state.update { it.copy(scanDirectories = current) }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            cacheManager.clearAllCache()
            loadCacheInfo()
        }
    }
}
