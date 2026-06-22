package com.melodyflow.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        CacheEntity::class,
        AIConfigEntity::class,
        AIRecommendationEntity::class,
        UserConversationEntity::class,
        CalendarEvent::class,
        EventSong::class,
        MusicDiary::class,
        ListeningLog::class,
        LocalSongEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun cacheDao(): CacheDao
    abstract fun aiConfigDao(): AIConfigDao
    abstract fun aiRecommendationDao(): AIRecommendationDao
    abstract fun userConversationDao(): UserConversationDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun eventSongDao(): EventSongDao
    abstract fun musicDiaryDao(): MusicDiaryDao
    abstract fun listeningLogDao(): ListeningLogDao
    abstract fun localSongDao(): LocalSongDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v6 schema is identical to v5; the real upgrade path is 5 -> 7
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to history table
                db.execSQL("ALTER TABLE history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE history ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")

                // Set lastPlayedAt = playedAt for existing records
                db.execSQL("UPDATE history SET lastPlayedAt = playedAt WHERE lastPlayedAt = 0")

                // Remove duplicates keeping the one with smallest dbId (earliest inserted)
                db.execSQL("DELETE FROM history WHERE dbId NOT IN (SELECT MIN(dbId) FROM history GROUP BY id)")

                // Create unique index on id column
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_id ON history(id)")

                // Create local_songs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_songs (
                        dbId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        filePath TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        duration INTEGER NOT NULL DEFAULT 0,
                        fileSize INTEGER NOT NULL DEFAULT 0,
                        lastModified INTEGER NOT NULL DEFAULT 0,
                        dateAdded INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_songs_filePath ON local_songs(filePath)")

                // Create calendar_events table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS calendar_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        eventType TEXT NOT NULL,
                        startTime INTEGER,
                        endTime INTEGER,
                        moodTag TEXT,
                        customMood TEXT,
                        hasAlarm INTEGER NOT NULL DEFAULT 0,
                        alarmTime INTEGER,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create event_songs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_songs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        songId TEXT NOT NULL,
                        songName TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        coverUrl TEXT,
                        playType TEXT NOT NULL DEFAULT 'song',
                        playData TEXT,
                        orderIndex INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(eventId) REFERENCES calendar_events(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)

                // Create music_diaries table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS music_diaries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        songId TEXT,
                        moodBefore TEXT,
                        moodAfter TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create listening_logs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS listening_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId TEXT NOT NULL,
                        songName TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        listenedAt INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        eventId INTEGER
                    )
                """)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to history table
                db.execSQL("ALTER TABLE history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE history ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")

                // Set lastPlayedAt = playedAt for existing records
                db.execSQL("UPDATE history SET lastPlayedAt = playedAt WHERE lastPlayedAt = 0")

                // Remove duplicates keeping the one with smallest dbId (earliest inserted)
                db.execSQL("DELETE FROM history WHERE dbId NOT IN (SELECT MIN(dbId) FROM history GROUP BY id)")

                // Create unique index on id column
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_id ON history(id)")

                // Create local_songs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_songs (
                        dbId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        filePath TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        duration INTEGER NOT NULL DEFAULT 0,
                        fileSize INTEGER NOT NULL DEFAULT 0,
                        lastModified INTEGER NOT NULL DEFAULT 0,
                        dateAdded INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_songs_filePath ON local_songs(filePath)")
            }
        }

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "melodyflow_db"
                ).addMigrations(MIGRATION_5_6, MIGRATION_5_7, MIGRATION_6_7).build().also { INSTANCE = it }
            }
        }
    }
}