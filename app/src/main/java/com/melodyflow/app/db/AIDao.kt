package com.melodyflow.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AIConfigDao {
    @Query("SELECT * FROM ai_config WHERE id = 1")
    suspend fun getConfig(): AIConfigEntity?

    @Query("SELECT * FROM ai_config WHERE id = 1")
    fun getConfigFlow(): Flow<AIConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: AIConfigEntity)

    @Query("DELETE FROM ai_config")
    suspend fun clearConfig()
}

@Dao
interface AIRecommendationDao {
    @Query("SELECT * FROM ai_recommendations ORDER BY timestamp DESC")
    fun getAllRecommendations(): Flow<List<AIRecommendationEntity>>

    @Query("SELECT * FROM ai_recommendations WHERE id = :id")
    suspend fun getRecommendationById(id: Long): AIRecommendationEntity?

    @Insert
    suspend fun insertRecommendation(recommendation: AIRecommendationEntity): Long

    @Query("UPDATE ai_recommendations SET isPlayed = :isPlayed WHERE id = :id")
    suspend fun updatePlayedStatus(id: Long, isPlayed: Boolean)

    @Query("DELETE FROM ai_recommendations WHERE id = :id")
    suspend fun deleteRecommendation(id: Long)

    @Query("DELETE FROM ai_recommendations")
    suspend fun clearAllRecommendations()
}

@Dao
interface UserConversationDao {
    @Query("SELECT * FROM user_conversations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConversations(limit: Int = 50): Flow<List<UserConversationEntity>>

    @Query("SELECT * FROM user_conversations WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getConversationsByType(type: String, limit: Int = 20): Flow<List<UserConversationEntity>>

    @Insert
    suspend fun insertConversation(conversation: UserConversationEntity): Long

    @Query("DELETE FROM user_conversations WHERE timestamp < :timestamp")
    suspend fun deleteOldConversations(timestamp: Long)

    @Query("DELETE FROM user_conversations")
    suspend fun clearAllConversations()
}
