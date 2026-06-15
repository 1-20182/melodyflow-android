package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI推荐会话记录实体
 */
@Entity(tableName = "ai_recommendations")
data class AIRecommendationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userPrompt: String?,
    val playlistName: String,
    val explanation: String,
    val songsJson: String,  // JSON格式的推荐歌曲列表
    val isPlayed: Boolean = false
)

/**
 * 用户对话记录实体
 */
@Entity(tableName = "user_conversations")
data class UserConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userMessage: String,
    val aiResponse: String,
    val type: String  // RECOMMENDATION, FEEDBACK, CHAT
)
