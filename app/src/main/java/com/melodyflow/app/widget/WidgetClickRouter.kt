package com.melodyflow.app.widget

import android.content.Context
import android.content.Intent
import com.melodyflow.app.R

object WidgetClickRouter {
    fun openCalendarEvent(context: Context, date: Long) {
        val intent = Intent(context, MusicCalendarConfigActivity::class.java).apply {
            putExtra("date", date)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openSettings(context: Context) {
        val intent = Intent(context, MusicCalendarSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun playSong(context: Context, songId: String) {
        val intent = Intent("com.melodyflow.app.action.PLAY_SONG").apply {
            putExtra("song_id", songId)
            `package` = context.packageName
        }
        context.sendBroadcast(intent)
    }
}