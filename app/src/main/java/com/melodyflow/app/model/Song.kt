package com.melodyflow.app.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// ============================================================
// SearchType enum
// ============================================================

enum class SearchType(val type: String) {
    SONG("song"),
    ARTIST("artist"),
    ALBUM("album")
}

// ============================================================
// Album data class
// ============================================================

@Parcelize
data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val pic: String = "",
    val songCount: Int = 0,
    val description: String = ""
) : Parcelable {
    fun getCoverUrl(): String {
        return if (pic.startsWith("http://") || pic.startsWith("https://")) {
            pic
        } else {
            ""
        }
    }
}

// ============================================================
// Artist data class
// ============================================================

@Parcelize
data class Artist(
    val id: String,
    val name: String,
    val pic: String = "",
    val songCount: Int = 0,
    val description: String = ""
) : Parcelable {
    fun getCoverUrl(): String {
        return if (pic.startsWith("http://") || pic.startsWith("https://")) {
            pic
        } else {
            ""
        }
    }
}

// ============================================================
// Song
// ============================================================

@Parcelize
data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String,
    val url: String? = null,
    val lrc: String? = null,
    val duration: Long = 0,
    val isCached: Boolean = false,
    val source: String = "netease"
) : Parcelable {
    fun getCoverUrl(): String {
        // Only return valid HTTP/HTTPS URLs, filter out local paths
        return if (pic.startsWith("http://") || pic.startsWith("https://")) {
            pic
        } else {
            ""
        }
    }
}

// ============================================================
// SearchResult (API raw response mapping)
// ============================================================

data class SearchResult(
    @SerializedName("name") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("album") val album: String = "",
    @SerializedName("pic") val pic: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("lrc") val lrc: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("duration") val duration: Long = 0
)

// ============================================================
// Chart
// ============================================================

data class Chart(
    val id: String,
    val name: String,
    val server: String
) {
    // Return empty string to use placeholder - hardcoded CDN URLs are often expired
    fun getCoverUrl(): String = ""
}

// ============================================================
// UnplayableSongsHolder - tracks songs that failed to play
// ============================================================
object UnplayableSongsHolder {
    private val unplayableIds = mutableSetOf<String>()
    private var prefs: android.content.SharedPreferences? = null
    private val listeners = mutableListOf<() -> Unit>()
    private const val RETRY_DELAY_MS = 30 * 60 * 1000L // 30 minutes
    private const val TIMESTAMP_SUFFIX = "_ts"  // Shorter suffix to avoid conflicts

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("unplayable_songs", android.content.Context.MODE_PRIVATE)
        unplayableIds.clear()
        
        val allPrefs = prefs?.all ?: emptyMap()
        val now = System.currentTimeMillis()
        
        // Find all song IDs (keys that have corresponding timestamp keys)
        for ((key, _) in allPrefs) {
            // Skip timestamp keys directly
            if (key.endsWith(TIMESTAMP_SUFFIX)) continue
            
            // Check if this key has a corresponding timestamp
            val timestampKey = "$key$TIMESTAMP_SUFFIX"
            val timestamp = allPrefs[timestampKey] as? Long ?: 0L
            
            // Only add if within retry window
            if (now - timestamp < RETRY_DELAY_MS) {
                unplayableIds.add(key)
                android.util.Log.d("UnplayableSongs", "Loaded unplayable song: $key (added at ${timestamp})")
            } else {
                // Clean up expired entry
                prefs?.edit()?.apply {
                    remove(key)
                    remove(timestampKey)
                    apply()
                }
                android.util.Log.d("UnplayableSongs", "Cleaned up expired song: $key")
            }
        }
        android.util.Log.i("UnplayableSongs", "Init complete. Total unplayable songs: ${unplayableIds.size}")
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        val snapshot = listeners.toList()
        snapshot.forEach { it.invoke() }
    }

    fun add(songId: String) {
        unplayableIds.add(songId)
        val timestampKey = "$songId$TIMESTAMP_SUFFIX"
        val now = System.currentTimeMillis()
        // Save with timestamp for expiration check (synchronous write)
        prefs?.edit()?.apply {
            putBoolean(songId, true)
            putLong(timestampKey, now)
            commit()
        }
        android.util.Log.i("UnplayableSongs", "Added song to unplayable list: $songId at $now")
        notifyChanged()
    }

    fun contains(songId: String): Boolean = unplayableIds.contains(songId)

    fun clear() {
        unplayableIds.clear()
        prefs?.edit()?.clear()?.apply()
        notifyChanged()
    }

    fun remove(songId: String) {
        unplayableIds.remove(songId)
        val timestampKey = "$songId$TIMESTAMP_SUFFIX"
        // Use commit() for synchronous write to ensure it's saved before app exit
        prefs?.edit()?.apply {
            remove(songId)
            remove(timestampKey)
            commit()
        }
        android.util.Log.i("UnplayableSongs", "Removed song from unplayable list: $songId")
        notifyChanged()
    }
}
