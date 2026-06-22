package com.melodyflow.app.recommendation

import android.content.Context
import com.melodyflow.app.db.MusicDatabase
import java.util.*

class RecommendationCache(private val context: Context) {

    fun getCachedRecommendation(): RecommendationResult? {
        // In production, read from Room recommendation_cache table
        // For now, use a simple SharedPreferences cache
        val prefs = context.getSharedPreferences("recommendation_cache", Context.MODE_PRIVATE)
        val songId = prefs.getString("today_song_id", null) ?: return null
        return RecommendationResult(
            songId = songId,
            songName = prefs.getString("today_song_name", "") ?: "",
            artist = prefs.getString("today_artist", "") ?: "",
            coverUrl = prefs.getString("today_cover_url", null),
            reason = prefs.getString("today_reason", "") ?: "",
            score = prefs.getFloat("today_score", 0f)
        )
    }

    fun cacheRecommendation(result: RecommendationResult) {
        val prefs = context.getSharedPreferences("recommendation_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("today_song_id", result.songId)
            putString("today_song_name", result.songName)
            putString("today_artist", result.artist)
            putString("today_cover_url", result.coverUrl)
            putString("today_reason", result.reason)
            putFloat("today_score", result.score)
            putLong("cached_at", System.currentTimeMillis())
            apply()
        }
    }

    fun isCacheValid(): Boolean {
        val prefs = context.getSharedPreferences("recommendation_cache", Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong("cached_at", 0L)
        if (cachedAt == 0L) return false
        // Cache valid for 3 hours
        return (System.currentTimeMillis() - cachedAt) < 3 * 3600 * 1000L
    }

    fun clearCache() {
        context.getSharedPreferences("recommendation_cache", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}