package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index(value = ["id"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val dbId: Long = 0,
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String,
    val url: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
)
