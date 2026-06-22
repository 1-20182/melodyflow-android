package com.melodyflow.app.db

import androidx.room.*

@Dao
interface EventSongDao {
    @Query("SELECT * FROM event_songs WHERE eventId = :eventId ORDER BY orderIndex ASC")
    suspend fun getSongsForEvent(eventId: Long): List<EventSong>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: EventSong): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<EventSong>)

    @Delete
    suspend fun deleteSong(song: EventSong)

    @Query("DELETE FROM event_songs WHERE eventId = :eventId")
    suspend fun deleteSongsForEvent(eventId: Long)

    // Sync for Widget
    @Query("SELECT * FROM event_songs WHERE eventId = :eventId ORDER BY orderIndex ASC")
    fun getSongsForEventSync(eventId: Long): List<EventSong>

    @Query("SELECT * FROM event_songs ORDER BY eventId ASC, orderIndex ASC")
    suspend fun getAllOnce(): List<EventSong>
}