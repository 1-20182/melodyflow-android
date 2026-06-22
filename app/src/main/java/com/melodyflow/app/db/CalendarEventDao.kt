package com.melodyflow.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE date >= :start AND date < :end ORDER BY date ASC")
    suspend fun getEventsInRange(start: Long, end: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE date = :date ORDER BY startTime ASC")
    suspend fun getEventsByDate(date: Long): List<CalendarEvent>

    @Query("SELECT DISTINCT date FROM calendar_events WHERE date >= :start AND date < :end")
    suspend fun getDatesWithEvents(start: Long, end: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEvent): Long

    @Update
    suspend fun updateEvent(event: CalendarEvent)

    @Delete
    suspend fun deleteEvent(event: CalendarEvent)

    @Query("DELETE FROM calendar_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Long)

    // 供 Widget 使用的同步查询
    @Query("SELECT * FROM calendar_events WHERE date >= :start AND date < :end ORDER BY date ASC")
    fun getEventsInRangeSync(start: Long, end: Long): List<CalendarEvent>
    
    // Flow for observing events
    @Query("SELECT * FROM calendar_events WHERE date = :date ORDER BY startTime ASC")
    fun observeEventsByDate(date: Long): Flow<List<CalendarEvent>>
    
    @Query("SELECT * FROM calendar_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): CalendarEvent?

    @Query("SELECT * FROM calendar_events ORDER BY date ASC")
    suspend fun getAllOnce(): List<CalendarEvent>
}