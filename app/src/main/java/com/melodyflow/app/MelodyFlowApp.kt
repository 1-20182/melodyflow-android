package com.melodyflow.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.data.MusicRepository
import com.melodyflow.app.model.UnplayableSongsHolder
import java.lang.ref.WeakReference

class MelodyFlowApp : Application() {

    val repository by lazy { MusicRepository.getInstance(this) }
    val cacheManager by lazy { CacheManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "MelodyFlow 初始化开始")
        Log.i(TAG, "版本: ${BuildConfig.VERSION_NAME ?: "unknown"}")

        // Initialize repository (triggers lazy init)
        try {
            repository
            Log.i(TAG, "MusicRepository 初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "MusicRepository 初始化失败", e)
        }

        // Initialize unplayable songs holder - CLEAR ALL previously saved unplayable songs
        UnplayableSongsHolder.clear()
        UnplayableSongsHolder.init(this)
        Log.i(TAG, "UnplayableSongsHolder 初始化完成，所有不可播放记录已清除")

        // Install crash handler as last resort
        installCrashHandler()

        Log.i(TAG, "MelodyFlow 初始化完成")
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "未捕获异常 [${thread.name}]", throwable)

            // Show a brief toast on the main thread
            val appRef = WeakReference(this)
            Handler(Looper.getMainLooper()).post {
                val app = appRef.get()
                if (app != null) {
                    try {
                        Toast.makeText(app, "程序发生异常，即将重启", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        // Ignore if toast fails
                    }
                }
            }

            // Let the default handler handle the crash (restart app)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "MelodyFlow"

        @Volatile
        private var instance: MelodyFlowApp? = null

        fun getInstance(): MelodyFlowApp {
            return instance ?: throw IllegalStateException("MelodyFlowApp 未初始化")
        }

        fun getInstance(context: Context): MelodyFlowApp {
            return context.applicationContext as MelodyFlowApp
        }
    }
}
