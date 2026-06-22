package com.melodyflow.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 100")
    fun getAll(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: HistoryEntity)

    @Query("UPDATE history SET playCount = playCount + 1, lastPlayedAt = :playedAt, playedAt = :playedAt WHERE id = :songId")
    suspend fun updatePlayStats(songId: String, playedAt: Long)

    @Query("DELETE FROM history WHERE dbId NOT IN (SELECT dbId FROM history ORDER BY playedAt DESC LIMIT 200)")
    suspend fun trimOldRecords()

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM history ORDER BY playedAt DESC")
    suspend fun getAllOnce(): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): HistoryEntity?
}
