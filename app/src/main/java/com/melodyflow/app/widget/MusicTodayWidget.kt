package com.melodyflow.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.melodyflow.app.R

class MusicTodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = buildTodayView(context)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        // Only cancel if no other widget instances
    }

    private fun buildTodayView(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.music_today_widget)
        // Set today's date
        val cal = java.util.Calendar.getInstance()
        val dateStr = String.format(
            java.util.Locale.CHINESE,
            "%d月%d日 %s",
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            getDayOfWeekChinese(cal.get(java.util.Calendar.DAY_OF_WEEK))
        )
        views.setTextViewText(R.id.widget_today_date, dateStr)
        // Set click to open config
        val configIntent = Intent(context, MusicCalendarConfigActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, configIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_today_container, pendingIntent)
        return views
    }

    private fun getDayOfWeekChinese(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            java.util.Calendar.SUNDAY -> "周日"
            java.util.Calendar.MONDAY -> "周一"
            java.util.Calendar.TUESDAY -> "周二"
            java.util.Calendar.WEDNESDAY -> "周三"
            java.util.Calendar.THURSDAY -> "周四"
            java.util.Calendar.FRIDAY -> "周五"
            java.util.Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }

    companion object {
        const val ACTION_UPDATE_TODAY = "com.melodyflow.app.action.UPDATE_TODAY"
    }
}