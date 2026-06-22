package com.melodyflow.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalSongDao {
    @Query("SELECT * FROM local_songs ORDER BY title COLLATE NOCASE ASC")
    fun getAll(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE filePath = :path")
    suspend fun getByPath(path: String): LocalSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: LocalSongEntity)

    @Delete
    suspend fun delete(song: LocalSongEntity)

    @Query("DELETE FROM local_songs WHERE filePath NOT IN (:paths)")
    suspend fun deleteNotInPaths(paths: List<String>)

    @Query("SELECT COUNT(*) FROM local_songs")
    suspend fun getCount(): Int

    @Query("DELETE FROM local_songs")
    suspend fun clearAll()
}
