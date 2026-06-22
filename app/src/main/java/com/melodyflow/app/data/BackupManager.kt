package com.melodyflow.app.data

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.melodyflow.app.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val favorites: List<FavoriteBackup> = emptyList(),
    val history: List<HistoryBackup> = emptyList(),
    val calendarEvents: List<CalendarEventBackup> = emptyList(),
    val eventSongs: List<EventSongBackup> = emptyList(),
    val diaries: List<DiaryBackup> = emptyList(),
    val aiConfig: AiConfigBackup? = null
)

data class FavoriteBackup(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String,
    val url: String? = null,
    val source: String = "netease"
)

data class HistoryBackup(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String,
    val playCount: Int = 1,
    val lastPlayedAt: Long = 0
)

data class CalendarEventBackup(
    val id: Long,
    val date: Long,
    val title: String,
    val description: String? = null,
    val eventType: String,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val moodTag: String? = null,
    val hasAlarm: Boolean = false,
    val alarmTime: Long? = null
)

data class EventSongBackup(
    val id: Long,
    val eventId: Long,
    val songId: String,
    val songName: String,
    val artist: String,
    val coverUrl: String? = null,
    val playType: String = "song",
    val orderIndex: Int = 0
)

data class DiaryBackup(
    val id: Long,
    val date: Long,
    val content: String,
    val songId: String? = null,
    val moodBefore: String? = null,
    val moodAfter: String? = null
)

data class AiConfigBackup(
    val provider: String,
    val model: String,
    val apiKey: String,
    val apiUrl: String,
    val isEnabled: Boolean = true
)

class BackupManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = MusicDatabase.getInstance(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        @Volatile
        private var INSTANCE: BackupManager? = null

        fun getInstance(context: Context): BackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        val BACKUP_DIR = File(Environment.getExternalStorageDirectory(), "MelodyFlow/backup")
        const val BACKUP_PREFIX = "melodyflow_backup_"
        const val AUTO_BACKUP_FILE = "melodyflow_auto_backup.json"
    }

    // ============================================================
    // Export
    // ============================================================

    suspend fun exportBackup(): File = withContext(Dispatchers.IO) {
        val data = collectBackupData()
        val json = gson.toJson(data)

        BACKUP_DIR.mkdirs()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val file = File(BACKUP_DIR, "${BACKUP_PREFIX}${dateFormat.format(Date())}.json")
        file.writeText(json)
        file
    }

    suspend fun autoBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = appContext.getSharedPreferences("melodyflow_backup", 0)
            val lastBackup = prefs.getLong("last_auto_backup", 0)
            // Only auto-backup if more than 5 minutes since last backup
            if (System.currentTimeMillis() - lastBackup < 5 * 60 * 1000L) return@withContext true

            val data = collectBackupData()
            val json = gson.toJson(data)

            BACKUP_DIR.mkdirs()
            val file = File(BACKUP_DIR, AUTO_BACKUP_FILE)
            file.writeText(json)

            prefs.edit().putLong("last_auto_backup", System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun collectBackupData(): BackupData {
        val favoriteDao = db.favoriteDao()
        val historyDao = db.historyDao()
        val calendarEventDao = db.calendarEventDao()
        val eventSongDao = db.eventSongDao()
        val musicDiaryDao = db.musicDiaryDao()
        val aiConfigDao = db.aiConfigDao()

        // Favorites
        val favorites = favoriteDao.getAllOnce().map { entity ->
            FavoriteBackup(
                id = entity.id,
                name = entity.name,
                artist = entity.artist,
                album = entity.album,
                pic = entity.pic,
                url = entity.url,
                source = "netease"
            )
        }

        // History
        val history = historyDao.getAllOnce().map { entity ->
            HistoryBackup(
                id = entity.id,
                name = entity.name,
                artist = entity.artist,
                album = entity.album,
                pic = entity.pic,
                playCount = entity.playCount,
                lastPlayedAt = entity.lastPlayedAt
            )
        }

        // Calendar events
        val calendarEvents = try {
            calendarEventDao.getAllOnce().map { entity ->
                CalendarEventBackup(
                    id = entity.id,
                    date = entity.date,
                    title = entity.title,
                    description = entity.description,
                    eventType = entity.eventType,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    moodTag = entity.moodTag,
                    hasAlarm = entity.hasAlarm,
                    alarmTime = entity.alarmTime
                )
            }
        } catch (e: Exception) { emptyList() }

        // Event songs
        val eventSongs = try {
            eventSongDao.getAllOnce().map { entity ->
                EventSongBackup(
                    id = entity.id,
                    eventId = entity.eventId,
                    songId = entity.songId,
                    songName = entity.songName,
                    artist = entity.artist,
                    coverUrl = entity.coverUrl,
                    playType = entity.playType,
                    orderIndex = entity.orderIndex
                )
            }
        } catch (e: Exception) { emptyList() }

        // Diaries
        val diaries = try {
            musicDiaryDao.getAllDiaries().map { entity ->
                DiaryBackup(
                    id = entity.id,
                    date = entity.date,
                    content = entity.content,
                    songId = entity.songId,
                    moodBefore = entity.moodBefore,
                    moodAfter = entity.moodAfter
                )
            }
        } catch (e: Exception) { emptyList() }

        // AI Config
        val aiConfig = try {
            aiConfigDao.getConfig()?.let { entity ->
                AiConfigBackup(
                    provider = entity.provider,
                    model = entity.model,
                    apiKey = entity.apiKey,
                    apiUrl = entity.apiUrl,
                    isEnabled = entity.isEnabled
                )
            }
        } catch (e: Exception) { null }

        return BackupData(
            favorites = favorites,
            history = history,
            calendarEvents = calendarEvents,
            eventSongs = eventSongs,
            diaries = diaries,
            aiConfig = aiConfig
        )
    }

    // ============================================================
    // Import
    // ============================================================

    data class ImportPreview(
        val favoriteCount: Int,
        val historyCount: Int,
        val calendarEventCount: Int,
        val diaryCount: Int,
        val hasAiConfig: Boolean,
        val exportDate: Long
    )

    fun previewBackup(file: File): ImportPreview? {
        return try {
            val json = file.readText()
            val data = gson.fromJson(json, BackupData::class.java)
            ImportPreview(
                favoriteCount = data.favorites.size,
                historyCount = data.history.size,
                calendarEventCount = data.calendarEvents.size,
                diaryCount = data.diaries.size,
                hasAiConfig = data.aiConfig != null,
                exportDate = data.exportDate
            )
        } catch (e: Exception) {
            null
        }
    }

    data class ImportOptions(
        val importFavorites: Boolean = true,
        val importHistory: Boolean = true,
        val importCalendar: Boolean = true,
        val importDiaries: Boolean = true,
        val importAiConfig: Boolean = true
    )

    suspend fun importBackup(file: File, options: ImportOptions): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = file.readText()
            val data = gson.fromJson(json, BackupData::class.java)

            val favoriteDao = db.favoriteDao()
            val historyDao = db.historyDao()
            val calendarEventDao = db.calendarEventDao()
            val eventSongDao = db.eventSongDao()
            val musicDiaryDao = db.musicDiaryDao()
            val aiConfigDao = db.aiConfigDao()

            // Import favorites (merge - add if not exist)
            if (options.importFavorites) {
                for (fav in data.favorites) {
                    val existing = favoriteDao.getById(fav.id)
                    if (existing == null) {
                        favoriteDao.insert(FavoriteEntity(
                            id = fav.id,
                            name = fav.name,
                            artist = fav.artist,
                            album = fav.album,
                            pic = fav.pic,
                            url = fav.url,
                            addedAt = System.currentTimeMillis()
                        ))
                    }
                }
            }

            // Import history (append)
            if (options.importHistory) {
                for (hist in data.history) {
                    val existing = historyDao.getByIdOnce(hist.id)
                    if (existing == null) {
                        historyDao.insert(HistoryEntity(
                            id = hist.id,
                            name = hist.name,
                            artist = hist.artist,
                            album = hist.album,
                            pic = hist.pic,
                            playedAt = hist.lastPlayedAt,
                            playCount = hist.playCount,
                            lastPlayedAt = hist.lastPlayedAt
                        ))
                    } else {
                        // Merge: update playCount if backup has more plays
                        if (hist.playCount > existing.playCount) {
                            historyDao.updatePlayStats(hist.id, hist.lastPlayedAt)
                        }
                    }
                }
            }

            // Import calendar events (merge by title+date)
            if (options.importCalendar) {
                try {
                    for (event in data.calendarEvents) {
                        calendarEventDao.insertEvent(CalendarEvent(
                            date = event.date,
                            title = event.title,
                            description = event.description,
                            eventType = event.eventType,
                            startTime = event.startTime,
                            endTime = event.endTime,
                            moodTag = event.moodTag,
                            hasAlarm = event.hasAlarm,
                            alarmTime = event.alarmTime
                        ))
                    }
                    for (song in data.eventSongs) {
                        eventSongDao.insertSong(EventSong(
                            eventId = song.eventId,
                            songId = song.songId,
                            songName = song.songName,
                            artist = song.artist,
                            coverUrl = song.coverUrl,
                            playType = song.playType,
                            orderIndex = song.orderIndex
                        ))
                    }
                } catch (e: Exception) { /* Calendar tables may not exist */ }
            }

            // Import diaries
            if (options.importDiaries) {
                try {
                    for (diary in data.diaries) {
                        musicDiaryDao.insertDiary(MusicDiary(
                            date = diary.date,
                            content = diary.content,
                            songId = diary.songId,
                            moodBefore = diary.moodBefore,
                            moodAfter = diary.moodAfter
                        ))
                    }
                } catch (e: Exception) { /* Diary table may not exist */ }
            }

            // Import AI config
            if (options.importAiConfig && data.aiConfig != null) {
                try {
                    val config = data.aiConfig
                    aiConfigDao.saveConfig(AIConfigEntity(
                        provider = config.provider,
                        model = config.model,
                        apiKey = config.apiKey,
                        apiUrl = config.apiUrl,
                        isEnabled = config.isEnabled
                    ))
                } catch (e: Exception) { /* AI config table may not exist */ }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    // Startup restore check
    // ============================================================

    fun hasBackupFile(): Boolean {
        val autoBackup = File(BACKUP_DIR, AUTO_BACKUP_FILE)
        if (autoBackup.exists()) return true

        // Also check for manual backup files
        BACKUP_DIR.mkdirs()
        val files = BACKUP_DIR.listFiles { file ->
            file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(".json")
        }
        return files?.isNotEmpty() == true
    }

    fun getLatestBackupFile(): File? {
        BACKUP_DIR.mkdirs()
        val autoBackup = File(BACKUP_DIR, AUTO_BACKUP_FILE)
        if (autoBackup.exists()) return autoBackup

        val files = BACKUP_DIR.listFiles { file ->
            file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(".json")
        }
        return files?.maxByOrNull { it.lastModified() }
    }

    suspend fun isDatabaseEmpty(): Boolean {
        val favoriteCount = db.favoriteDao().getCount()
        return favoriteCount == 0
    }
}
