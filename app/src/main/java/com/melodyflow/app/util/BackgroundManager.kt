package com.melodyflow.app.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.ViewGroup
import java.io.File

object BackgroundManager {

    private const val PREFS_NAME = "MelodyFlow"
    private const val KEY_GRADIENT_START = "gradient_start"
    private const val KEY_GRADIENT_END = "gradient_end"
    private const val BG_FILE_NAME = "custom_background.jpg"

    // Dark mode overlay alpha (0-255): adds a dark scrim on top of the background in dark mode
    private const val DARK_MODE_SCRIM_ALPHA = 0x33 // ~20% black overlay (reduced from 40%)

    // Default gradient colors for when no custom background is set
    private val DEFAULT_GRADIENT_START = Color.parseColor("#0D1117")
    private val DEFAULT_GRADIENT_END = Color.parseColor("#0A0E14")

    // LRU memory cache for background bitmaps
    private val bitmapCache: LruCache<String, Bitmap>

    init {
        // Use 1/8 of available memory for bitmap cache
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        bitmapCache = LruCache(cacheSize)
    }

    /**
     * Check if the system is currently in dark mode.
     */
    private fun isDarkMode(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Get the root layout view from an activity.
     * This is the content view that was set via setContentView().
     */
    private fun getRootLayout(activity: Activity): ViewGroup? {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
        return contentView?.getChildAt(0) as? ViewGroup
    }

    /**
     * Apply the appropriate background to the activity, considering dark mode.
     * In dark mode, a dark scrim overlay is applied on top of custom backgrounds
     * to ensure readability and visual consistency.
     */
    fun applyToActivity(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gradientStart = prefs.getInt(KEY_GRADIENT_START, 0)
        val gradientEnd = prefs.getInt(KEY_GRADIENT_END, 0)
        val bgFile = File(activity.filesDir, BG_FILE_NAME)
        val darkMode = isDarkMode(activity)

        // Get the root layout to apply background on top of the XML background
        val rootLayout = getRootLayout(activity)
        if (rootLayout == null) {
            android.util.Log.w("BackgroundManager", "Could not find root layout, falling back to decorView")
            return
        }

        if (bgFile.exists()) {
            android.util.Log.i("BackgroundManager", "Applying custom background: ${bgFile.absolutePath}, darkMode=$darkMode")

            // Check memory cache first
            val cacheKey = bgFile.absolutePath + if (darkMode) "_dark" else ""
            val cachedBitmap = bitmapCache.get(cacheKey)
            if (cachedBitmap != null) {
                android.util.Log.i("BackgroundManager", "Using cached bitmap")
                rootLayout.background = BitmapDrawable(activity.resources, cachedBitmap)
                return
            }

            // Load from disk with sampling to reduce memory
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(bgFile.absolutePath, options)

                android.util.Log.i("BackgroundManager", "Image dimensions: ${options.outWidth}x${options.outHeight}")

                // Get screen dimensions as fallback
                val displayMetrics = activity.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                // Calculate inSampleSize for memory efficiency
                val targetWidth = if (rootLayout.width > 0) rootLayout.width else screenWidth
                val targetHeight = if (rootLayout.height > 0) rootLayout.height else screenHeight

                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                android.util.Log.i("BackgroundManager", "Loading bitmap with inSampleSize: ${options.inSampleSize}")

                val bitmap = BitmapFactory.decodeFile(bgFile.absolutePath, options)
                if (bitmap != null) {
                    val finalBitmap = if (darkMode) {
                        applyDarkScrim(bitmap)
                    } else {
                        bitmap
                    }
                    bitmapCache.put(cacheKey, finalBitmap)
                    val drawable = BitmapDrawable(activity.resources, finalBitmap)
                    drawable.setAntiAlias(true)
                    rootLayout.background = drawable
                    android.util.Log.i("BackgroundManager", "Background applied successfully (darkMode=$darkMode)")
                } else {
                    android.util.Log.e("BackgroundManager", "Failed to decode bitmap")
                    applyDefaultBackground(rootLayout, darkMode)
                }
            } catch (e: Exception) {
                android.util.Log.e("BackgroundManager", "Error loading background", e)
                applyDefaultBackground(rootLayout, darkMode)
            }
        } else if (gradientStart != 0 && gradientEnd != 0) {
            android.util.Log.i("BackgroundManager", "Applying gradient background, darkMode=$darkMode")
            val (adjustedStart, adjustedEnd) = if (darkMode) {
                adjustGradientForDarkMode(gradientStart, gradientEnd)
            } else {
                gradientStart to gradientEnd
            }
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(adjustedStart, adjustedEnd)
            )
            rootLayout.background = drawable
        } else {
            // No custom background set - apply a subtle default gradient
            // instead of leaving the near-black #090909 from XML
            android.util.Log.i("BackgroundManager", "Applying default background gradient")
            applyDefaultBackground(rootLayout, darkMode)
        }
    }

    /**
     * Apply a default subtle gradient background when no custom background is set.
     * This prevents the UI from appearing as a flat black screen.
     */
    private fun applyDefaultBackground(rootLayout: ViewGroup, darkMode: Boolean) {
        val (start, end) = if (darkMode) {
            // In dark mode, use very subtle dark gradient with a hint of color
            DEFAULT_GRADIENT_START to DEFAULT_GRADIENT_END
        } else {
            // In light mode, use a slightly lighter default
            Color.parseColor("#F5F5F5") to Color.parseColor("#E8E8E8")
        }
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(start, end)
        )
        rootLayout.background = drawable
    }

    /**
     * Update the background when dark mode state changes.
     * Call this from Activity.onConfigurationChanged() when uiMode changes,
     * or from Activity.onCreate() after theme is applied.
     */
    fun updateForDarkMode(activity: Activity) {
        applyToActivity(activity)
    }

    /**
     * Apply a dark scrim overlay on top of the bitmap for dark mode.
     * This dims the background image to improve text readability.
     */
    private fun applyDarkScrim(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val scrimPaint = Paint().apply {
            color = Color.BLACK
            alpha = DARK_MODE_SCRIM_ALPHA
        }
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), scrimPaint)
        return result
    }

    /**
     * Adjust gradient colors for dark mode by darkening them.
     * Reduces brightness and increases opacity toward darker tones.
     */
    private fun adjustGradientForDarkMode(startColor: Int, endColor: Int): Pair<Int, Int> {
        return darkenColor(startColor) to darkenColor(endColor)
    }

    /**
     * Darken a color by blending it toward black.
     * The blend factor controls how much darker the result becomes.
     */
    private fun darkenColor(color: Int, factor: Float = 0.2f): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)
        return Color.argb(
            a,
            (r * (1 - factor)).toInt(),
            (g * (1 - factor)).toInt(),
            (b * (1 - factor)).toInt()
        )
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
