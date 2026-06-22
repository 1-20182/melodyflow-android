package com.melodyflow.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDiaryDao {
    @Query("SELECT * FROM music_diaries WHERE date = :date ORDER BY createdAt DESC")
    suspend fun getDiariesByDate(date: Long): List<MusicDiary>

    @Query("SELECT * FROM music_diaries ORDER BY date DESC, createdAt DESC")
    suspend fun getAllDiaries(): List<MusicDiary>

    @Query("SELECT * FROM music_diaries ORDER BY date DESC, createdAt DESC")
    fun observeAllDiaries(): Flow<List<MusicDiary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: MusicDiary): Long

    @Delete
    suspend fun deleteDiary(diary: MusicDiary)
}