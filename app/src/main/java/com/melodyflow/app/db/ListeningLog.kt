package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_logs")
data class ListeningLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val songName: String,
    val artist: String,
    val listenedAt: Long,
    val duration: Long,
    val eventId: Long? = null
)