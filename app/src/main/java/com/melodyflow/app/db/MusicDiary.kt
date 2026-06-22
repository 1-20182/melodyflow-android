package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_diaries")
data class MusicDiary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val content: String,
    val songId: String? = null,
    val moodBefore: String? = null,
    val moodAfter: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)