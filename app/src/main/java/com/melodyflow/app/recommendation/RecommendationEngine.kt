package com.melodyflow.app.recommendation

import android.content.Context
import com.melodyflow.app.db.ListeningLog
import com.melodyflow.app.db.MusicDatabase
import com.melodyflow.app.repository.WidgetRepository
import java.util.*

data class RecommendationResult(
    val songId: String,
    val songName: String,
    val artist: String,
    val coverUrl: String?,
    val reason: String, // e.g. "春季推荐", "根据您的运动日程推荐"
    val score: Float
)

class RecommendationEngine(private val context: Context) {

    private val repository = WidgetRepository(context)

    /**
     * Get daily recommendations combining all dimensions:
     * 1. Season-based genre matching
     * 2. Holiday detection
     * 3. Today's event type mapping
     * 4. User listening history
     * 5. AI API (if available)
     */
    suspend fun getDailyRecommendations(): List<RecommendationResult> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        val season = detectSeason(cal)
        val holiday = detectHoliday(cal)
        val eventType = getTodayEventType()
        val historyGenres = getHistoryPreferences(now)

        // Build reason strings
        val reasons = mutableListOf<String>()
        reasons.add("${season}推荐")
        if (holiday != null) reasons.add("${holiday}氛围")
        if (eventType != null) reasons.add("根据${eventType}日程推荐")
        if (historyGenres.isNotEmpty()) reasons.add("您常听的风格")

        // TODO: In production, query actual song database with these filters
        // For now return mock recommendations
        return listOf(
            RecommendationResult(
                songId = "mock_1",
                songName = "春日序曲",
                artist = "示例歌手",
                coverUrl = null,
                reason = reasons.joinToString(" · "),
                score = 0.95f
            )
        )
    }

    private fun detectSeason(cal: Calendar): String {
        val month = cal.get(Calendar.MONTH) + 1
        return when (month) {
            in 3..5 -> "春季"
            in 6..8 -> "夏季"
            in 9..11 -> "秋季"
            else -> "冬季"
        }
    }

    private fun detectHoliday(cal: Calendar): String? {
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return when {
            month == 1 && day == 1 -> "元旦"
            month == 2 && day == 14 -> "情人节"
            month == 3 && day == 8 -> "妇女节"
            month == 5 && day == 1 -> "劳动节"
            month == 6 && day == 1 -> "儿童节"
            month == 10 && day == 1 -> "国庆节"
            month == 12 && day == 24 -> "平安夜"
            month == 12 && day == 25 -> "圣诞节"
            else -> null
        }
    }

    private suspend fun getTodayEventType(): String? {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 86400000L

        val events = repository.getEventsInRangeSync(dayStart, dayEnd)
        return events.firstOrNull()?.eventType
    }

    private suspend fun getHistoryPreferences(now: Long): List<String> {
        val db = MusicDatabase.getInstance(context)
        val logDao = db.listeningLogDao()

        // Look at last 7 days of listening history
        val weekAgo = now - 7 * 24 * 3600 * 1000L
        val topSongs = logDao.getTopSongsInRange(weekAgo, now)

        // Map to genres (simplified - in production, query song metadata)
        return topSongs.take(3).map { "popular_genre_$it" }
    }
}