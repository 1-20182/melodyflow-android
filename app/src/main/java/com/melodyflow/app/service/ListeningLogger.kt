package com.melodyflow.app.service

import android.content.Context
import com.melodyflow.app.db.ListeningLog
import com.melodyflow.app.db.MusicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ListeningLogger {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun logPlayback(
        context: Context,
        songId: String,
        songName: String,
        artist: String,
        duration: Long
    ) {
        scope.launch {
            val db = MusicDatabase.getInstance(context)
            val logDao = db.listeningLogDao()
            val eventDao = db.calendarEventDao()
            val now = System.currentTimeMillis()

            val log = ListeningLog(
                songId = songId,
                songName = songName,
                artist = artist,
                listenedAt = now,
                duration = duration
            )
            val logId = logDao.insertLog(log)

            // Try to associate with overlapping calendar events
            try {
                val events = eventDao.getEventsInRangeSync(now - 3600000, now + 3600000)
                val matchingEvent = events.firstOrNull { event ->
                    // Check if event's time range overlaps with current time
                    if (event.startTime != null && event.endTime != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        val dayStart = cal.timeInMillis
                        val eventStartMs = dayStart + event.startTime * 60000
                        val eventEndMs = dayStart + event.endTime * 60000
                        now in eventStartMs..eventEndMs
                    } else {
                        // No specific time - associate by date
                        val eventDayStart = event.date
                        val eventDayEnd = eventDayStart + 86400000
                        now in eventDayStart until eventDayEnd
                    }
                }
                if (matchingEvent != null) {
                    logDao.associateLogWithEvent(logId, matchingEvent.id)
                }
            } catch (_: Exception) {
                // Silently fail association
            }
        }
    }
}