package com.melodyflow.app.repository

import android.content.Context
import com.melodyflow.app.db.CalendarEvent
import com.melodyflow.app.db.EventSong
import com.melodyflow.app.db.ListeningLog
import com.melodyflow.app.db.MusicDatabase
import com.melodyflow.app.db.SongPlayCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetRepository(private val context: Context) {

    private val database by lazy { MusicDatabase.getInstance(context) }

    suspend fun getEventsInRange(monthStart: Long, monthEnd: Long): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            database.calendarEventDao().getEventsInRange(monthStart, monthEnd)
        }
    }

    suspend fun getEventsByDate(date: Long): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            database.calendarEventDao().getEventsByDate(date)
        }
    }

    suspend fun getDatesWithEvents(start: Long, end: Long): List<Long> {
        return withContext(Dispatchers.IO) {
            database.calendarEventDao().getDatesWithEvents(start, end)
        }
    }

    suspend fun getRecommendedSongsForDate(date: Long): List<SongPlayCount> {
        return withContext(Dispatchers.IO) {
            val dayStart = date
            val dayEnd = date + 24 * 60 * 60 * 1000
            database.listeningLogDao().getTopSongsInRange(dayStart, dayEnd)
        }
    }

    suspend fun getSongsForEvent(eventId: Long): List<EventSong> {
        return withContext(Dispatchers.IO) {
            database.eventSongDao().getSongsForEvent(eventId)
        }
    }

    // 同步版本，用于 Widget 直接调用
    fun getEventsInRangeSync(monthStart: Long, monthEnd: Long): List<CalendarEvent> {
        return database.calendarEventDao().getEventsInRangeSync(monthStart, monthEnd)
    }

    fun getSongsForEventSync(eventId: Long): List<EventSong> {
        return database.eventSongDao().getSongsForEventSync(eventId)
    }

    suspend fun getUnassociatedLogsInRange(start: Long, end: Long): List<ListeningLog> {
        return withContext(Dispatchers.IO) {
            database.listeningLogDao().getUnassociatedLogsInRange(start, end)
        }
    }

    suspend fun associateLogWithEvent(logId: Long, eventId: Long) {
        withContext(Dispatchers.IO) {
            database.listeningLogDao().associateLogWithEvent(logId, eventId)
        }
    }

    suspend fun insertLog(log: ListeningLog): Long {
        return withContext(Dispatchers.IO) {
            database.listeningLogDao().insertLog(log)
        }
    }
}