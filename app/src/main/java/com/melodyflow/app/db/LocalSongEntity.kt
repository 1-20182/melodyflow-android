package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_songs", indices = [Index(value = ["filePath"], unique = true)])
data class LocalSongEntity(
    @PrimaryKey(autoGenerate = true)
    val dbId: Long = 0,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long = 0,
    val fileSize: Long = 0,
    val lastModified: Long = 0,
    val dateAdded: Long = System.currentTimeMillis()
)
