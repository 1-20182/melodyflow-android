package com.melodyflow.app.util

import com.melodyflow.app.model.LyricLine

/**
 * In-memory LRU cache for parsed lyrics.
 * Caches up to [MAX_SIZE] songs to avoid repeated network requests.
 * Uses a pure Kotlin implementation for JVM test compatibility.
 */
object LyricsCache {
    private const val MAX_SIZE = 20

    private val cache = object : LinkedHashMap<String, List<LyricLine>>(MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>?): Boolean {
            return size > MAX_SIZE
        }
    }

    @Synchronized
    fun get(songId: String): List<LyricLine>? {
        val result = cache[songId]
        if (result != null) {
            Logger.d("LyricsCache", "Cache hit for songId=$songId")
        } else {
            Logger.d("LyricsCache", "Cache miss for songId=$songId")
        }
        return result
    }

    @Synchronized
    fun put(songId: String, lyrics: List<LyricLine>) {
        cache[songId] = lyrics
        Logger.d("LyricsCache", "Cached ${lyrics.size} lines for songId=$songId")
    }

    @Synchronized
    fun clear() {
        cache.clear()
        Logger.d("LyricsCache", "Cache cleared")
    }
}