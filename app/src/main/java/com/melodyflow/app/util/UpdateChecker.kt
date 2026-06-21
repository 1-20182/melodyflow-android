package com.melodyflow.app.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.melodyflow.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val changelog: String,
    val downloadUrl: String
)

/**
 * Checks for app updates from the GitHub Releases API.
 * Automatically falls back to mirror sources if the primary API is unreachable.
 */
class UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("body") val body: String?,
        @SerializedName("html_url") val htmlUrl: String,
        @SerializedName("prerelease") val prerelease: Boolean = false
    )

    private companion object {
        private const val GITHUB_API = "https://api.github.com/repos/1-20182/melodyflow-android/releases/latest"

        // Mirror sources for regions where GitHub is not directly accessible
        private val MIRROR_URLS = listOf(
            "https://ghproxy.com/$GITHUB_API",
            "https://hub.gitmirror.com/api.github.com/repos/1-20182/melodyflow-android/releases/latest"
        )
    }

    /**
     * Check for the latest release from GitHub, with automatic mirror fallback.
     * Returns UpdateInfo indicating whether a newer version is available.
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        // Try primary URL first, then mirrors
        val urlsToTry = listOf(GITHUB_API) + MIRROR_URLS

        for (url in urlsToTry) {
            val result = tryFetchRelease(url)
            if (result != null) {
                val latestTag = result.tagName.removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME
                val hasUpdate = compareVersions(latestTag, currentVersion) > 0

                Log.i("UpdateChecker", "Fetched release info from: $url")
                return@withContext UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = result.tagName,
                    changelog = result.body ?: "",
                    downloadUrl = result.htmlUrl
                )
            }
            Log.w("UpdateChecker", "Failed to fetch from: $url, trying next mirror...")
        }

        Log.e("UpdateChecker", "All sources failed, no update info available")
        UpdateInfo(false, "", "", "")
    }

    /**
     * Try to fetch release info from a single URL.
     * Returns the parsed GitHubRelease or null on failure.
     */
    private fun tryFetchRelease(url: String): GitHubRelease? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("UpdateChecker", "URL $url returned ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            gson.fromJson(body, GitHubRelease::class.java)
        } catch (e: Exception) {
            Log.w("UpdateChecker", "URL $url error: ${e.message}")
            null
        }
    }

    /**
     * Compare two semantic version strings.
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}