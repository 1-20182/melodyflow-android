package com.melodyflow.app.util

/**
 * Structured logging utility with unified tag format.
 * All logs use the "MelodyFlow" tag with [location] prefix for easy filtering.
 */
object Logger {
    private const val TAG = "MelodyFlow"

    fun d(location: String, message: String) {
        android.util.Log.d(TAG, "[$location] $message")
    }

    fun e(location: String, message: String, throwable: Throwable? = null) {
        android.util.Log.e(TAG, "[$location] $message", throwable)
    }

    fun w(location: String, message: String) {
        android.util.Log.w(TAG, "[$location] $message")
    }
}