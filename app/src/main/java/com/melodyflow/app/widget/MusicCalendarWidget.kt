package com.melodyflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.melodyflow.app.R

class MusicCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = buildMonthView(context)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.schedulePeriodic(context)
        WidgetUpdateWorker.scheduleDailyRefresh(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateWorker.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MONTH_CHANGED -> handleMonthChange(context, intent)
            ACTION_DATE_CLICKED -> handleDateClick(context, intent)
            ACTION_TODAY_CLICKED -> handleTodayClick(context)
        }
        super.onReceive(context, intent)
    }

    private fun buildMonthView(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.music_calendar_widget)
        // Set month title
        views.setTextViewText(R.id.widget_month_title, getCurrentMonthTitle())
        // Set click listeners for navigation
        views.setOnClickPendingIntent(R.id.widget_prev_month, createPendingIntent(context, ACTION_PREV_MONTH))
        views.setOnClickPendingIntent(R.id.widget_next_month, createPendingIntent(context, ACTION_NEXT_MONTH))
        views.setOnClickPendingIntent(R.id.widget_today_button, createPendingIntent(context, ACTION_TODAY_CLICKED))
        // Date grid clicks would need RemoteViewsService for dynamic cells
        return views
    }

    private fun handleMonthChange(context: Context, intent: Intent) {
        val direction = intent.getIntExtra("direction", 0)
        // Update stored month offset and trigger refresh
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, MusicCalendarWidget::class.java)
        )
        onUpdate(context, appWidgetManager, widgetIds)
    }

    private fun handleDateClick(context: Context, intent: Intent) {
        val date = intent.getLongExtra("date", 0L)
        val openIntent = Intent(context, MusicCalendarConfigActivity::class.java).apply {
            putExtra("date", date)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(openIntent)
    }

    private fun handleTodayClick(context: Context) {
        // Reset to current month and refresh
        // Re-trigger onUpdate
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, MusicCalendarWidget::class.java)
        )
        onUpdate(context, appWidgetManager, widgetIds)
    }

    private fun createPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicCalendarWidget::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getCurrentMonthTitle(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}年${cal.get(java.util.Calendar.MONTH) + 1}月"
    }

    companion object {
        const val ACTION_MONTH_CHANGED = "com.melodyflow.app.action.MONTH_CHANGED"
        const val ACTION_PREV_MONTH = "com.melodyflow.app.action.PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.melodyflow.app.action.NEXT_MONTH"
        const val ACTION_DATE_CLICKED = "com.melodyflow.app.action.DATE_CLICKED"
        const val ACTION_TODAY_CLICKED = "com.melodyflow.app.action.TODAY_CLICKED"
        const val ACTION_WIDGET_UPDATE = "com.melodyflow.app.action.WIDGET_UPDATE"
    }
}