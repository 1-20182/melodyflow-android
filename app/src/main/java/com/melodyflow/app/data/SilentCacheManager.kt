package com.melodyflow.app.data

import android.content.Context
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.melodyflow.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 静默缓存管理器
 * 在后台执行缓存，使用 Snackbar 显示进度和结果
 */
object SilentCacheManager {

    /**
     * 静默缓存单首歌曲
     * @param context 上下文
     * @param song 要缓存的歌曲
     * @param rootView 用于显示 Snackbar 的根视图
     * @param onComplete 完成回调 (success: Boolean)
     */
    suspend fun cacheSong(
        context: Context,
        song: Song,
        rootView: View,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val cacheManager = CacheManager.getInstance(context)
        val musicRepository = MusicRepository.getInstance(context)

        // 显示开始缓存的 Snackbar
        showSnackbar(rootView, "开始缓存: ${song.name}", Snackbar.LENGTH_SHORT)

        try {
            // 检查是否已缓存
            val existingCache = withContext(Dispatchers.IO) {
                cacheManager.getCacheEntity(song.id)
            }
            if (existingCache != null) {
                showSnackbar(rootView, "已缓存: ${song.name}", Snackbar.LENGTH_SHORT)
                onComplete?.invoke(true)
                return
            }

            // 获取歌曲 URL
            val pairResult = withContext(Dispatchers.IO) {
                musicRepository.getSongUrlWithFallback(song.id)
            }
            val url = pairResult.first
            val source = pairResult.second

            if (url.isNullOrBlank()) {
                showSnackbar(rootView, "缓存失败: 无法获取链接", Snackbar.LENGTH_LONG)
                onComplete?.invoke(false)
                return
            }

            // 更新歌曲 URL
            val songWithUrl = song.copy(url = url, source = source?.name ?: song.source)

            // 执行缓存
            val success = withContext(Dispatchers.IO) {
                cacheManager.cacheSong(songWithUrl)
            }

            if (success) {
                showSnackbar(rootView, "缓存成功: ${song.name}", Snackbar.LENGTH_SHORT)
            } else {
                showSnackbar(rootView, "缓存失败: ${song.name}", Snackbar.LENGTH_LONG)
            }

            onComplete?.invoke(success)
        } catch (e: Exception) {
            showSnackbar(rootView, "缓存失败: ${e.message}", Snackbar.LENGTH_LONG)
            onComplete?.invoke(false)
        }
    }

    /**
     * 静默缓存多首歌曲
     * @param context 上下文
     * @param songs 要缓存的歌曲列表
     * @param rootView 用于显示 Snackbar 的根视图
     * @param onProgress 进度回调 (current: Int, total: Int)
     * @param onComplete 完成回调 (successCount: Int, failedCount: Int)
     */
    suspend fun cacheSongs(
        context: Context,
        songs: List<Song>,
        rootView: View,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: ((Int, Int) -> Unit)? = null
    ) {
        if (songs.isEmpty()) {
            showSnackbar(rootView, "没有歌曲需要缓存", Snackbar.LENGTH_SHORT)
            onComplete?.invoke(0, 0)
            return
        }

        val cacheManager = CacheManager.getInstance(context)
        val musicRepository = MusicRepository.getInstance(context)

        showSnackbar(rootView, "开始缓存 ${songs.size} 首歌曲...", Snackbar.LENGTH_SHORT)

        var successCount = 0
        var failedCount = 0

        for ((index, song) in songs.withIndex()) {
            onProgress?.invoke(index + 1, songs.size)

            try {
                // 检查是否已缓存
                val existingCache = withContext(Dispatchers.IO) {
                    cacheManager.getCacheEntity(song.id)
                }
                if (existingCache != null) {
                    successCount++
                    continue
                }

                // 获取歌曲 URL
                val pairResult = withContext(Dispatchers.IO) {
                    musicRepository.getSongUrlWithFallback(song.id)
                }
                val url = pairResult.first
                val source = pairResult.second

                if (url.isNullOrBlank()) {
                    failedCount++
                    continue
                }

                // 更新歌曲 URL
                val songWithUrl = song.copy(url = url, source = source?.name ?: song.source)

                // 执行缓存
                val success = withContext(Dispatchers.IO) {
                    cacheManager.cacheSong(songWithUrl)
                }

                if (success) {
                    successCount++
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                failedCount++
            }
        }

        // 显示最终结果
        val message = if (failedCount == 0) {
            "缓存完成: 成功 $successCount 首"
        } else {
            "缓存完成: 成功 $successCount 首, 失败 $failedCount 首"
        }
        showSnackbar(rootView, message, Snackbar.LENGTH_LONG)

        onComplete?.invoke(successCount, failedCount)
    }

    private fun showSnackbar(rootView: View, message: String, duration: Int) {
        try {
            Snackbar.make(rootView, message, duration).show()
        } catch (e: Exception) {
            // Snackbar 可能因为视图不可用而失败
            e.printStackTrace()
        }
    }
}