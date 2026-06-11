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

        if (bgFile.exists()) {
            // Check memory cache first
            val cacheKey = bgFile.absolutePath
            val cachedBitmap = bitmapCache.get(cacheKey)
            if (cachedBitmap != null) {
                decorView.background = BitmapDrawable(activity.resources, cachedBitmap)
                return
            }

            // Load from disk with sampling to reduce memory
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(bgFile.absolutePath, options)

                // Calculate inSampleSize for memory efficiency
                options.inSampleSize = calculateInSampleSize(options, decorView.width, decorView.height)
                options.inJustDecodeBounds = false

                val bitmap = BitmapFactory.decodeFile(bgFile.absolutePath, options)
                if (bitmap != null) {
                    bitmapCache.put(cacheKey, bitmap)
                    decorView.background = BitmapDrawable(activity.resources, bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (gradientStart != 0 && gradientEnd != 0) {
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(gradientStart, gradientEnd)
            )
            decorView.background = drawable
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
