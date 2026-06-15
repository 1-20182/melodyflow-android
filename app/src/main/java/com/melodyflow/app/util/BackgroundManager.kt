package com.melodyflow.app.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import java.io.File

object BackgroundManager {

    private const val PREFS_NAME = "MelodyFlow"
    private const val KEY_GRADIENT_START = "gradient_start"
    private const val KEY_GRADIENT_END = "gradient_end"
    private const val BG_FILE_NAME = "custom_background.jpg"

    // LRU memory cache for background bitmaps
    private val bitmapCache: LruCache<String, Bitmap>

    init {
        // Use 1/8 of available memory for bitmap cache
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        bitmapCache = LruCache(cacheSize)
    }

    fun applyToActivity(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gradientStart = prefs.getInt(KEY_GRADIENT_START, 0)
        val gradientEnd = prefs.getInt(KEY_GRADIENT_END, 0)
        val bgFile = File(activity.filesDir, BG_FILE_NAME)
        val decorView = activity.window.decorView

        // 清除之前的背景
        decorView.background = null

        if (bgFile.exists()) {
            android.util.Log.i("BackgroundManager", "Applying custom background: ${bgFile.absolutePath}")

            // Check memory cache first
            val cacheKey = bgFile.absolutePath
            val cachedBitmap = bitmapCache.get(cacheKey)
            if (cachedBitmap != null) {
                android.util.Log.i("BackgroundManager", "Using cached bitmap")
                decorView.background = BitmapDrawable(activity.resources, cachedBitmap)
                return
            }

            // Load from disk with sampling to reduce memory
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(bgFile.absolutePath, options)

                android.util.Log.i("BackgroundManager", "Image dimensions: ${options.outWidth}x${options.outHeight}")

                // 获取屏幕尺寸作为后备
                val displayMetrics = activity.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                // Calculate inSampleSize for memory efficiency
                // 使用屏幕尺寸而不是decorView尺寸，因为decorView可能还没有布局完成
                val targetWidth = if (decorView.width > 0) decorView.width else screenWidth
                val targetHeight = if (decorView.height > 0) decorView.height else screenHeight

                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565 // 节省内存

                android.util.Log.i("BackgroundManager", "Loading bitmap with inSampleSize: ${options.inSampleSize}")

                val bitmap = BitmapFactory.decodeFile(bgFile.absolutePath, options)
                if (bitmap != null) {
                    bitmapCache.put(cacheKey, bitmap)
                    val drawable = BitmapDrawable(activity.resources, bitmap)
                    drawable.setAntiAlias(true)
                    decorView.background = drawable
                    android.util.Log.i("BackgroundManager", "Background applied successfully")
                } else {
                    android.util.Log.e("BackgroundManager", "Failed to decode bitmap")
                }
            } catch (e: Exception) {
                android.util.Log.e("BackgroundManager", "Error loading background", e)
                e.printStackTrace()
            }
        } else if (gradientStart != 0 && gradientEnd != 0) {
            android.util.Log.i("BackgroundManager", "Applying gradient background")
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(gradientStart, gradientEnd)
            )
            decorView.background = drawable
        } else {
            android.util.Log.i("BackgroundManager", "No custom background set")
        }
    }

    /**
     * Clear the bitmap cache to free memory
     */
    fun clearCache() {
        bitmapCache.evictAll()
    }

    /**
     * Calculate an appropriate inSampleSize for decoding bitmaps.
     * Reduces memory usage by sampling down large images.
     *
     * @param options BitmapFactory.Options with outWidth/outHeight populated
     * @param reqWidth Target width
     * @param reqHeight Target height
     * @return Power-of-two inSampleSize (2 or 4 typically)
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2
            // and keeps both height and width larger than the requested height and width
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        // Clamp to reasonable values: 2 or 4
        return inSampleSize.coerceIn(1, 4)
    }
}
