package com.melodyflow.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.melodyflow.app.R
import com.melodyflow.app.data.CacheManager
import com.melodyflow.app.data.MusicRepository
import com.melodyflow.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CacheProgressActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SONGS = "songs"

        fun start(context: Context, songs: List<Song>) {
            val intent = Intent(context, CacheProgressActivity::class.java)
            intent.putParcelableArrayListExtra(EXTRA_SONGS, ArrayList(songs))
            context.startActivity(intent)
        }
    }

    private val viewModel: CacheProgressViewModel by viewModels()

    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvProgressText: TextView
    private lateinit var linearProgress: ProgressBar
    private lateinit var songProgressLayout: View
    private lateinit var songProgress: ProgressBar
    private lateinit var tvSongProgress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnCancel: View
    private lateinit var btnOk: View

    private val songs: List<Song> by lazy {
        intent.getParcelableArrayListExtra<Song>(EXTRA_SONGS) ?: emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_progress)

        initViews()
        setupObservers()
        startCaching()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvCurrentSong = findViewById(R.id.tvCurrentSong)
        tvProgressText = findViewById(R.id.tvProgressText)
        linearProgress = findViewById(R.id.linearProgress)
        songProgressLayout = findViewById(R.id.songProgressLayout)
        songProgress = findViewById(R.id.songProgress)
        tvSongProgress = findViewById(R.id.tvSongProgress)
        tvStatus = findViewById(R.id.tvStatus)
        btnCancel = findViewById(R.id.btnCancel)
        btnOk = findViewById(R.id.btnOk)

        btnCancel.setOnClickListener {
            viewModel.cancel()
        }

        btnOk.setOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        viewModel.currentSong.observe(this) { song ->
            tvCurrentSong.text = song?.let { "${it.name} - ${it.artist}" } ?: ""
        }

        viewModel.progress.observe(this) { progress ->
            linearProgress.progress = progress
        }

        viewModel.progressText.observe(this) { text ->
            tvProgressText.text = text
        }

        viewModel.songProgress.observe(this) { progress ->
            if (progress >= 0) {
                songProgressLayout.visibility = View.VISIBLE
                songProgress.progress = progress
                songProgress.isIndeterminate = false
                tvSongProgress.text = "$progress%"
            } else {
                songProgress.isIndeterminate = true
                tvSongProgress.text = "准备中..."
            }
        }

        viewModel.status.observe(this) { status ->
            tvStatus.text = status
        }

        viewModel.isCancelled.observe(this) { isCancelled ->
            if (isCancelled) {
                tvTitle.text = "已取消"
                btnCancel.visibility = View.GONE
                btnOk.visibility = View.VISIBLE
                btnOk.isEnabled = true
                tvCurrentSong.text = "缓存任务已取消"
                songProgressLayout.visibility = View.GONE
            }
        }

        viewModel.isFinished.observe(this) { isFinished ->
            if (isFinished) {
                tvTitle.text = "缓存完成"
                btnCancel.visibility = View.GONE
                btnOk.visibility = View.VISIBLE
                btnOk.isEnabled = true
                songProgressLayout.visibility = View.GONE

                val successCount = viewModel.successCount
                val failedCount = viewModel.failedCount
                tvCurrentSong.text = "成功缓存 $successCount 首歌曲"
                if (failedCount > 0) {
                    tvStatus.text = "失败 $failedCount 首"
                }
            }
        }
    }

    private fun startCaching() {
        viewModel.startCaching(this, songs)
    }
}

class CacheProgressViewModel : ViewModel() {

    val currentSong = MutableLiveData<Song?>()
    val progress = MutableLiveData(0)
    val progressText = MutableLiveData("0/0")
    val songProgress = MutableLiveData(-1)
    val status = MutableLiveData("")
    val isCancelled = MutableLiveData(false)
    val isFinished = MutableLiveData(false)

    var successCount = 0
        private set
    var failedCount = 0
        private set

    private var cacheJob: Job? = null

    fun startCaching(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) {
            isFinished.value = true
            return
        }

        cacheJob = viewModelScope.launch {
            val cacheManager = CacheManager.getInstance(context)
            val musicRepository = MusicRepository.getInstance(context)

            val total = songs.size
            var current = 0

            progressText.value = "0/$total"
            progress.value = 0

            for (song in songs) {
                if (isCancelled.value == true) break

                currentSong.value = song

                // Check if already cached
                val existingCache = withContext(Dispatchers.IO) {
                    cacheManager.getCacheEntity(song.id)
                }
                if (existingCache != null) {
                    successCount++
                    current++
                    updateProgress(current, total)
                    continue
                }

                // Try to get the song URL with fallback
                var url: String? = null
                var source: com.melodyflow.app.model.MusicSource? = null

                android.util.Log.i("CacheProgress", "Attempting to get URL for song: ${song.name} (ID: ${song.id})")

                try {
                    val pairResult = musicRepository.getSongUrlWithFallback(song.id)
                    url = pairResult.first
                    source = pairResult.second
                    android.util.Log.i("CacheProgress", "Got URL: $url from source: ${source?.server}")
                } catch (e: Exception) {
                    android.util.Log.e("CacheProgress", "Failed to get URL for song ${song.id}", e)
                }

                if (url.isNullOrBlank()) {
                    android.util.Log.e("CacheProgress", "URL is null or blank for song ${song.id}")
                    failedCount++
                    current++
                    updateProgress(current, total)
                    status.value = "无法获取歌曲链接: ${song.name}"
                    continue
                }

                // Update song with proper URL and source
                val songWithUrl = song.copy(url = url, source = source?.name ?: song.source)

                // Download the song
                songProgress.postValue(0)
                status.postValue("正在下载: ${song.name}")

                val success = cacheSongWithProgress(context, songWithUrl) { percent ->
                    songProgress.postValue(percent)
                }

                if (success) {
                    successCount++
                    status.value = "缓存成功: ${song.name}"
                } else {
                    failedCount++
                    status.value = "缓存失败: ${song.name}"
                }

                current++
                updateProgress(current, total)

                // Short delay between songs
                delay(200)
            }

            songProgress.value = -1
            isFinished.value = true
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        progressText.value = "$current/$total"
        if (total > 0) {
            progress.value = (current * 100 / total)
        }
    }

    private suspend fun cacheSongWithProgress(
        context: Context,
        song: Song,
        progressCallback: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheManager = CacheManager.getInstance(context)
        cacheManager.cacheSong(song, progressCallback)
    }

    fun cancel() {
        isCancelled.value = true
        cacheJob?.cancel()
    }
}
