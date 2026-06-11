package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey
    val songId: String,
    val songName: String,
    val artist: String,
    val album: String = "",
    val pic: String = "",
    val lrc: String = "",
    val filePath: String,
    val fileSize: Long,
    val cachedAt: Long = System.currentTimeMillis(),
    val infoCompleted: Boolean = false  // 标记是否已完成信息补全
)