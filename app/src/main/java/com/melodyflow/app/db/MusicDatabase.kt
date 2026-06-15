package com.melodyflow.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        CacheEntity::class,
        AIConfigEntity::class,
        AIRecommendationEntity::class,
        UserConversationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun cacheDao(): CacheDao
    abstract fun aiConfigDao(): AIConfigDao
    abstract fun aiRecommendationDao(): AIRecommendationDao
    abstract fun userConversationDao(): UserConversationDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "melodyflow_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}