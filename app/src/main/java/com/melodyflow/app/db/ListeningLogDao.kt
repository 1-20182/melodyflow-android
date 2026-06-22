package com.melodyflow.app.db

import androidx.room.*

@Dao
interface ListeningLogDao {
    @Query("SELECT * FROM listening_logs WHERE listenedAt >= :start AND listenedAt < :end ORDER BY listenedAt DESC")
    suspend fun getLogsInRange(start: Long, end: Long): List<ListeningLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ListeningLog): Long

    @Query("SELECT songId, COUNT(*) as playCount FROM listening_logs WHERE listenedAt >= :start AND listenedAt < :end GROUP BY songId ORDER BY playCount DESC")
    suspend fun getTopSongsInRange(start: Long, end: Long): List<SongPlayCount>

    @Query("SELECT * FROM listening_logs WHERE eventId IS NULL AND listenedAt >= :start AND listenedAt <= :end")
    suspend fun getUnassociatedLogsInRange(start: Long, end: Long): List<ListeningLog>

    @Query("UPDATE listening_logs SET eventId = :eventId WHERE id = :logId")
    suspend fun associateLogWithEvent(logId: Long, eventId: Long)
}

data class SongPlayCount(
    val songId: String,
    val playCount: Int
)