package com.melodyflow.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import com.melodyflow.app.R
import com.melodyflow.app.repository.WidgetRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = WidgetRepository(applicationContext)
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val themeHelper = WidgetThemeHelper.getTheme(applicationContext)

            // Update Month Calendar Widgets
            val calendarComponent = ComponentName(applicationContext, MusicCalendarWidget::class.java)
            val calendarWidgetIds = appWidgetManager.getAppWidgetIds(calendarComponent)

            val now = Calendar.getInstance()
            val monthStart = getMonthStart(now)
            val monthEnd = getMonthEnd(now)
            val events = repository.getEventsInRangeSync(monthStart, monthEnd)
            val datesWithEvents = events.map { it.date }.distinct()

            calendarWidgetIds.forEach { widgetId ->
                val views = RemoteViews(applicationContext.packageName, R.layout.music_calendar_widget)
                views.setTextViewText(R.id.widget_month_title,
                    "${now.get(Calendar.YEAR)}年${now.get(Calendar.MONTH) + 1}月")

                // Apply theme
                WidgetThemeHelper.applyThemeToViews(views, themeHelper)

                // Set navigation click intents
                views.setOnClickPendingIntent(R.id.widget_prev_month,
                    createPendingIntent(applicationContext, MusicCalendarWidget.ACTION_PREV_MONTH))
                views.setOnClickPendingIntent(R.id.widget_next_month,
                    createPendingIntent(applicationContext, MusicCalendarWidget.ACTION_NEXT_MONTH))
                views.setOnClickPendingIntent(R.id.widget_today_button,
                    createPendingIntent(applicationContext, MusicCalendarWidget.ACTION_TODAY_CLICKED))

                appWidgetManager.updateAppWidget(widgetId, views)
            }

            // Update Today Widgets
            val todayComponent = ComponentName(applicationContext, MusicTodayWidget::class.java)
            val todayWidgetIds = appWidgetManager.getAppWidgetIds(todayComponent)
            val todayCal = Calendar.getInstance()
            val todayDateStr = String.format(
                java.util.Locale.CHINESE,
                "%d月%d日 %s",
                todayCal.get(Calendar.MONTH) + 1,
                todayCal.get(Calendar.DAY_OF_MONTH),
                getDayOfWeekChinese(todayCal.get(Calendar.DAY_OF_WEEK))
            )

            todayWidgetIds.forEach { widgetId ->
                val views = RemoteViews(applicationContext.packageName, R.layout.music_today_widget)
                views.setTextViewText(R.id.widget_today_date, todayDateStr)

                // Try to show today's events count
                val todayStart = getDayStart(todayCal)
                val todayEnd = todayStart + 86400000L
                val todayEvents = repository.getEventsInRangeSync(todayStart, todayEnd)
                if (todayEvents.isNotEmpty()) {
                    views.setTextViewText(R.id.widget_today_schedule, "今日 ${todayEvents.size} 个日程")
                } else {
                    views.setTextViewText(R.id.widget_today_schedule, "今日无日程")
                }

                // Apply theme
                WidgetThemeHelper.applyThemeToViews(views, themeHelper)

                appWidgetManager.updateAppWidget(widgetId, views)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun getMonthStart(cal: Calendar): Long {
        val clone = cal.clone() as Calendar
        clone.set(Calendar.DAY_OF_MONTH, 1)
        clone.set(Calendar.HOUR_OF_DAY, 0)
        clone.set(Calendar.MINUTE, 0)
        clone.set(Calendar.SECOND, 0)
        clone.set(Calendar.MILLISECOND, 0)
        return clone.timeInMillis
    }

    private fun getMonthEnd(cal: Calendar): Long {
        val clone = cal.clone() as Calendar
        clone.set(Calendar.DAY_OF_MONTH, clone.getActualMaximum(Calendar.DAY_OF_MONTH))
        clone.set(Calendar.HOUR_OF_DAY, 23)
        clone.set(Calendar.MINUTE, 59)
        clone.set(Calendar.SECOND, 59)
        clone.set(Calendar.MILLISECOND, 999)
        return clone.timeInMillis
    }

    private fun getDayStart(cal: Calendar): Long {
        val clone = cal.clone() as Calendar
        clone.set(Calendar.HOUR_OF_DAY, 0)
        clone.set(Calendar.MINUTE, 0)
        clone.set(Calendar.SECOND, 0)
        clone.set(Calendar.MILLISECOND, 0)
        return clone.timeInMillis
    }

    private fun createPendingIntent(context: Context, action: String): android.app.PendingIntent {
        val intent = Intent(context, MusicCalendarWidget::class.java).apply { this.action = action }
        return android.app.PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getDayOfWeekChinese(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SUNDAY -> "周日"; Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"; Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"; Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"; else -> ""
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "widget_update", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun scheduleDailyRefresh(context: Context) {
            // Schedule a daily refresh at midnight
            val dailyRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(calculateDelayUntilMidnight(), TimeUnit.MILLISECONDS)
                .addTag("widget_daily_refresh")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_daily_refresh", ExistingWorkPolicy.REPLACE, dailyRequest)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("widget_update")
            WorkManager.getInstance(context).cancelUniqueWork("widget_daily_refresh")
        }

        private fun calculateDelayUntilMidnight(): Long {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 24)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis - now
        }
    }
}