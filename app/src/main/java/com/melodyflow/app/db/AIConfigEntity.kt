package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_config")
data class AIConfigEntity(
    @PrimaryKey
    val id: Int = 1,  // 单条记录
    val provider: String,
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val isEnabled: Boolean
)
