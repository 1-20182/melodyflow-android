package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String,
    val url: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
