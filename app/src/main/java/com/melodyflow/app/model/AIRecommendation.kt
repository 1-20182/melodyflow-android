package com.melodyflow.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * AI推荐配置数据类
 */
data class AIConfig(
    val provider: AIProvider,
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val isEnabled: Boolean = false
)

/**
 * AI提供商枚举
 */
enum class AIProvider(val displayName: String, val defaultUrl: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-3.5-turbo"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1/messages", "claude-3-haiku-20240307"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/models", "gemini-pro"),
    ASTRBOT("AstrBot", "", ""),  // 自定义AstrBot地址
    CUSTOM("自定义", "", "");

    companion object {
        fun fromString(name: String): AIProvider {
            return values().find { it.name == name } ?: CUSTOM
        }
    }
}

/**
 * AI推荐请求数据
 */
data class AIRecommendationRequest(
    val favoriteSongs: List<Song>,
    val recentHistory: List<Song>,
    val userPrompt: String? = null,
    val limit: Int = 10
)

/**
 * AI推荐响应数据
 */
@Parcelize
data class AIRecommendationResponse(
    val recommendations: List<AIRecommendedSong>,
    val explanation: String,
    val playlistName: String
) : Parcelable

/**
 * AI推荐的单曲
 */
@Parcelize
data class AIRecommendedSong(
    val songName: String,
    val artist: String,
    val album: String? = null,
    val reason: String? = null,
    val coverUrl: String? = null
) : Parcelable

/**
 * AI推荐会话记录
 */
data class AIRecommendationSession(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userPrompt: String?,
    val playlistName: String,
    val explanation: String,
    val songs: List<AIRecommendedSong>,
    val isPlayed: Boolean = false
)

/**
 * 用户对话记录
 */
data class UserConversation(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userMessage: String,
    val aiResponse: String,
    val type: ConversationType
)

enum class ConversationType {
    RECOMMENDATION,  // 推荐请求
    FEEDBACK,        // 用户反馈
    CHAT             // 普通对话
}
