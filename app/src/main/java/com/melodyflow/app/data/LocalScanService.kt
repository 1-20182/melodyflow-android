package com.melodyflow.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.melodyflow.app.db.LocalSongDao
import com.melodyflow.app.db.LocalSongEntity
import com.melodyflow.app.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalScanService private constructor(context: Context) {

    private val localSongDao: LocalSongDao = MusicDatabase.getInstance(context).localSongDao()
    private val appContext = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: LocalScanService? = null

        fun getInstance(context: Context): LocalScanService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalScanService(context.applicationContext).also { INSTANCE = it }
            }
        }

        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus")
    }

    /**
     * Get default scan directories
     */
    fun getDefaultScanDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        // Standard Music directory
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { dirs.add(it) }

        // Downloads directory
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }

        // MelodyFlow cache directory
        File(Environment.getExternalStorageDirectory(), "MelodyFlow/cache/music").let { dirs.add(it) }

        return dirs.filter { it.exists() && it.isDirectory }
    }

    /**
     * Get custom scan directories from SharedPreferences
     */
    fun getCustomScanDirectories(): List<File> {
        val prefs = appContext.getSharedPreferences("melodyflow_settings", 0)
        val paths = prefs.getStringSet("scan_directories", emptySet()) ?: emptySet()
        return paths.mapNotNull { path ->
            val file = File(path)
            if (file.exists() && file.isDirectory) file else null
        }
    }

    /**
     * Perform incremental scan - only scan files that are new or modified
     */
    suspend fun incrementalScan(onProgress: ((Int, Int) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        val directories = getDefaultScanDirectories() + getCustomScanDirectories()
        val audioFiles = findAudioFiles(directories)
        var scanned = 0

        for (file in audioFiles) {
            val existing = localSongDao.getByPath(file.absolutePath)

            // Skip if file hasn't been modified since last scan
            if (existing != null && existing.lastModified == file.lastModified()) {
                continue
            }

            val entity = extractMetadata(file)
            if (entity != null) {
                localSongDao.insert(entity)
                scanned++
            }

            onProgress?.invoke(scanned, audioFiles.size)
        }

        // Remove entries for files that no longer exist
        val allPaths = audioFiles.map { it.absolutePath }
        localSongDao.deleteNotInPaths(allPaths)

        scanned
    }

    /**
     * Perform full scan - rescan all files regardless of modification time
     */
    suspend fun fullScan(onProgress: ((Int, Int) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        val directories = getDefaultScanDirectories() + getCustomScanDirectories()
        val audioFiles = findAudioFiles(directories)
        var scanned = 0

        for (file in audioFiles) {
            val entity = extractMetadata(file)
            if (entity != null) {
                localSongDao.insert(entity)
                scanned++
            }
            onProgress?.invoke(scanned, audioFiles.size)
        }

        // Remove entries for files that no longer exist
        val allPaths = audioFiles.map { it.absolutePath }
        localSongDao.deleteNotInPaths(allPaths)

        scanned
    }

    private fun findAudioFiles(directories: List<File>): List<File> {
        val result = mutableListOf<File>()
        for (dir in directories) {
            scanDirectory(dir, result)
        }
        return result
    }

    private fun scanDirectory(dir: File, result: MutableList<File>) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDirectory(file, result)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in SUPPORTED_EXTENSIONS) {
                        result.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            // Permission denied or other IO error, skip
        }
    }

    private fun extractMetadata(file: File): LocalSongEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "未知艺术家"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "未知专辑"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            LocalSongEntity(
                filePath = file.absolutePath,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                fileSize = file.length(),
                lastModified = file.lastModified()
            )
        } catch (e: Exception) {
            // Failed to extract metadata, create basic entry
            LocalSongEntity(
                filePath = file.absolutePath,
                title = file.nameWithoutExtension,
                artist = "未知艺术家",
                album = "未知专辑",
                duration = 0L,
                fileSize = file.length(),
                lastModified = file.lastModified()
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
