package com.melodyflow.app.model

import android.content.Context
import android.content.SharedPreferences

// 音乐源枚举
enum class MusicSource(val server: String, val displayName: String) {
    NETEASE("netease", "网易云音乐"),
    QQ("tencent", "QQ音乐")
}

// 音乐源管理器
object MusicSourceManager {
    private const val PREFS_NAME = "music_source"
    private const val KEY_CURRENT_SOURCE = "current_source"
    
    private var prefs: SharedPreferences? = null
    private val listeners = mutableListOf<(MusicSource) -> Unit>()
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getCurrentSource(): MusicSource {
        val sourceName = prefs?.getString(KEY_CURRENT_SOURCE, MusicSource.NETEASE.name) ?: MusicSource.NETEASE.name
        return try {
            MusicSource.valueOf(sourceName)
        } catch (e: Exception) {
            MusicSource.NETEASE
        }
    }
    
    fun setCurrentSource(source: MusicSource) {
        prefs?.edit()?.putString(KEY_CURRENT_SOURCE, source.name)?.apply()
        notifyListeners(source)
    }
    
    fun addListener(listener: (MusicSource) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (MusicSource) -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners(source: MusicSource) {
        val snapshot = listeners.toList()
        snapshot.forEach { it.invoke(source) }
    }
    
    fun getOtherSource(source: MusicSource): MusicSource {
        return if (source == MusicSource.NETEASE) MusicSource.QQ else MusicSource.NETEASE
    }
}
