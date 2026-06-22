package com.melodyflow.app.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.melodyflow.app.R
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class MusicAlarmWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getLong("eventId", -1L)
        val songId = inputData.getString("songId") ?: return Result.failure()
        val targetVolume = inputData.getFloat("targetVolume", 0.5f)

        return try {
            // Phase 1: Vibrate for 3 seconds (intermittent bursts)
            vibrateProgressive(applicationContext)

            // Phase 2: Volume ramp - start playback muted, gradually increase over 5 seconds
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // Note: In production, set actual song data source here
            // mediaPlayer.setDataSource(songUrl)

            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.prepare()
            mediaPlayer.start()

            // Ramp volume from 0 to target over 5 seconds
            for (i in 1..10) {
                val volume = (targetVolume * i) / 10f
                mediaPlayer.setVolume(volume, volume)
                delay(500) // 500ms * 10 = 5 seconds
            }

            // Phase 3: Show full-screen notification
            showAlarmNotification(applicationContext, eventId)

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun vibrateProgressive(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Intermittent short burst pattern: vibrate 200ms, pause 100ms, repeat
        val pattern = longArrayOf(0, 200, 100, 200, 100, 200, 100, 200, 100, 200, 300, 200, 300, 200, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun showAlarmNotification(context: Context, eventId: Long) {
        val channelId = "music_alarm_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "音乐闹钟", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "音乐闹钟通知"
                enableVibration(true)
                setSound(null, null) // Sound managed by MediaPlayer
            }
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(context, MusicAlarmWorker::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("音乐闹钟")
            .setContentText("日程时间到！正在播放音乐...")
            .setSmallIcon(R.drawable.ic_widget_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MusicCalendarSettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ), true
            )
            .addAction(android.R.drawable.ic_media_pause, "暂停", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(eventId.toInt(), notification)
    }

    companion object {
        fun scheduleAlarm(
            context: Context,
            eventId: Long,
            songId: String,
            alarmTimeMinutes: Long, // minutes from midnight
            targetVolume: Float = 0.5f
        ) {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val alarmMillis = cal.timeInMillis + alarmTimeMinutes * 60 * 1000

            if (alarmMillis <= now) return // Already passed

            val delay = alarmMillis - now
            val inputData = Data.Builder()
                .putLong("eventId", eventId)
                .putString("songId", songId)
                .putFloat("targetVolume", targetVolume)
                .build()

            val request = OneTimeWorkRequestBuilder<MusicAlarmWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("alarm_$eventId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "alarm_$eventId", ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancelAlarm(context: Context, eventId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("alarm_$eventId")
        }
    }
}