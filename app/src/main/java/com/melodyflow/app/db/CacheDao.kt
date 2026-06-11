package com.melodyflow.app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache")
    fun getAll(): Flow<List<CacheEntity>>

    @Query("SELECT * FROM cache WHERE songId = :songId")
    suspend fun getBySongId(songId: String): CacheEntity?

    @Insert
    suspend fun insert(cache: CacheEntity)

    @Delete
    suspend fun delete(cache: CacheEntity)

    @Query("DELETE FROM cache WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cache")
    suspend fun getCount(): Int

    @Query("SELECT SUM(fileSize) FROM cache")
    suspend fun getTotalSize(): Long

    @Query("SELECT COUNT(*) FROM cache WHERE infoCompleted = 0 OR songName = '未知歌曲' OR artist = '未知艺术家' OR pic = ''")
    suspend fun countNeedsCompletion(): Int
}