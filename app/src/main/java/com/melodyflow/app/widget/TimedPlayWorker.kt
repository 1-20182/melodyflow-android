package com.melodyflow.app.widget

import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class TimedPlayWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        // Send broadcast to stop music playback
        val stopIntent = Intent("com.melodyflow.app.action.STOP_PLAYBACK").apply {
            `package` = applicationContext.packageName
        }
        applicationContext.sendBroadcast(stopIntent)
        return Result.success()
    }

    companion object {
        fun scheduleStop(context: Context, delayMinutes: Long, requestId: String = "timed_play") {
            val request = OneTimeWorkRequestBuilder<TimedPlayWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(requestId)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(requestId, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelStop(context: Context, requestId: String = "timed_play") {
            WorkManager.getInstance(context).cancelUniqueWork(requestId)
        }
    }
}