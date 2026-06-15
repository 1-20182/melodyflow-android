package com.melodyflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.melodyflow.app.MelodyFlowApp
import com.melodyflow.app.R
import com.melodyflow.app.databinding.ActivityAiRecommendationBinding
import com.melodyflow.app.data.ApiResult
import com.melodyflow.app.model.*
import com.melodyflow.app.service.AIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AIRecommendationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiRecommendationBinding
    private val repository by lazy { (application as MelodyFlowApp).repository }
    private lateinit var adapter: AIRecommendationAdapter
    private var aiConfig: AIConfig? = null

    // 场景标签映射
    private val scenarioMap = mapOf(
        R.id.chipWork to "我想听一些适合工作时听的轻音乐，帮助我保持专注",
        R.id.chipRelax to "我想听一些放松的音乐，缓解压力",
        R.id.chipExercise to "我想听一些节奏感强的音乐，适合运动健身",
        R.id.chipSleep to "我想听一些轻柔舒缓的音乐，帮助入睡",
        R.id.chipParty to "我想听一些欢快的音乐，适合聚会派对",
        R.id.chipStudy to "我想听一些适合学习时听的背景音乐"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiRecommendationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupScenarioChips()
        setupListeners()
        checkAIConfig()
        loadUserStats()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AIRecommendationAdapter(
            onPlayClick = { session ->
                playRecommendation(session)
            },
            onDeleteClick = { session ->
                showDeleteConfirmDialog(session)
            },
            onShareClick = { session ->
                shareRecommendation(session)
            }
        )
        binding.rvRecommendations.layoutManager = LinearLayoutManager(this)
        binding.rvRecommendations.adapter = adapter
    }

    private fun setupScenarioChips() {
        binding.chipGroupScenarios.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chipId = checkedIds[0]
                val prompt = scenarioMap[chipId]
                if (prompt != null) {
                    binding.editUserInput.setText(prompt)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnGetRecommendations.setOnClickListener {
            getAIRecommendations()
        }

        binding.btnGoToSettings.setOnClickListener {
            startActivity(Intent(this, AISettingsActivity::class.java))
        }

        binding.btnClearHistory.setOnClickListener {
            showClearHistoryConfirmDialog()
        }
    }

    private fun loadUserStats() {
        lifecycleScope.launch {
            // 加载收藏数量
            val favorites = repository.getFavorites().first()
            binding.tvFavoriteCount.text = favorites.size.toString()

            // 加载播放历史数量
            val history = repository.getHistory().first()
            binding.tvHistoryCount.text = history.size.toString()

            // 加载推荐次数
            val recommendations = repository.getAIRecommendations().first()
            binding.tvRecommendationCount.text = recommendations.size.toString()
        }
    }

    private fun checkAIConfig() {
        lifecycleScope.launch {
            aiConfig = repository.getAIConfig()
            if (aiConfig?.isEnabled != true) {
                showNotConfiguredState()
            } else {
                showMainContent()
                loadRecommendations()
            }
        }
    }

    private fun showNotConfiguredState() {
        binding.notConfiguredLayout.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
    }

    private fun showMainContent() {
        binding.notConfiguredLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
    }

    private fun loadRecommendations() {
        lifecycleScope.launch {
            repository.getAIRecommendations().collect { sessions ->
                if (sessions.isNotEmpty()) {
                    adapter.submitList(sessions)
                    binding.emptyLayout.visibility = View.GONE
                    binding.rvRecommendations.visibility = View.VISIBLE
                } else {
                    binding.emptyLayout.visibility = View.VISIBLE
                    binding.rvRecommendations.visibility = View.GONE
                }
                // 更新推荐次数
                binding.tvRecommendationCount.text = sessions.size.toString()
            }
        }
    }

    private fun getAIRecommendations() {
        val userInput = binding.editUserInput.text.toString().trim()

        if (userInput.isBlank()) {
            Toast.makeText(this, "请输入你的需求或选择场景", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            showLoading(true)

            try {
                val config = aiConfig
                if (config == null || !config.isEnabled) {
                    Toast.makeText(this@AIRecommendationActivity, "请先配置AI设置", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 获取用户收藏和播放历史
                val favorites = repository.getFavorites().first().map { entity ->
                    Song(
                        id = entity.id,
                        name = entity.name,
                        artist = entity.artist,
                        album = entity.album,
                        pic = entity.pic,
                        url = entity.url
                    )
                }

                val history = repository.getHistory().first().take(20).map { entity ->
                    Song(
                        id = entity.id,
                        name = entity.name,
                        artist = entity.artist,
                        album = entity.album,
                        pic = entity.pic,
                        url = entity.url
                    )
                }

                val request = AIRecommendationRequest(
                    favoriteSongs = favorites,
                    recentHistory = history,
                    userPrompt = userInput,
                    limit = 10
                )

                val service = AIService(config)
                val response = service.getRecommendations(request)

                if (response != null) {
                    // 保存推荐会话
                    val session = AIRecommendationSession(
                        userPrompt = userInput,
                        playlistName = response.playlistName,
                        explanation = response.explanation,
                        songs = response.recommendations
                    )
                    repository.saveAIRecommendation(session)

                    Toast.makeText(
                        this@AIRecommendationActivity,
                        "已生成推荐：${response.playlistName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.editUserInput.text?.clear()
                    binding.chipGroupScenarios.clearCheck()
                } else {
                    Toast.makeText(
                        this@AIRecommendationActivity,
                        "获取推荐失败，请稍后重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AIRecommendationActivity,
                    "错误：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnGetRecommendations.isEnabled = !show
    }

    private fun playRecommendation(session: AIRecommendationSession) {
        lifecycleScope.launch {
            showLoading(true)
            binding.tvLoadingText?.text = "正在准备歌曲..."

            try {
                // 并行搜索推荐的歌曲并生成播放列表
                val songs = mutableListOf<Song>()
                val totalSongs = session.songs.size
                var processedCount = 0

                // 使用 async 并行搜索所有歌曲
                val deferredResults = session.songs.map { recommendedSong ->
                    async(Dispatchers.IO) {
                        try {
                            val searchResult = repository.search("${recommendedSong.songName} ${recommendedSong.artist}")
                            if (searchResult is ApiResult.Success) {
                                val searchData = searchResult.data
                                // 找到最匹配的歌曲
                                val matchedSong = searchData.firstOrNull { song: Song ->
                                    song.name.contains(recommendedSong.songName, ignoreCase = true) ||
                                    recommendedSong.songName.contains(song.name, ignoreCase = true)
                                } ?: searchData.firstOrNull()
                                matchedSong
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                // 等待所有搜索完成并收集结果
                deferredResults.forEachIndexed { index, deferred ->
                    val song = deferred.await()
                    song?.let { songs.add(it) }
                    processedCount++
                    // 更新加载提示
                    withContext(Dispatchers.Main) {
                        binding.tvLoadingText?.text = "正在准备歌曲... (${processedCount}/${totalSongs})"
                    }
                }

                showLoading(false)

                if (songs.isNotEmpty()) {
                    // 保存到SongListHolder并播放
                    SongListHolder.songs = songs

                    val intent = Intent(this@AIRecommendationActivity, PlayerActivity::class.java).apply {
                        putExtra("clickIndex", 0)
                        putExtra("autoPlay", true) // 自动播放
                    }
                    startActivity(intent)

                    // 标记为已播放
                    repository.markRecommendationAsPlayed(session.id)

                    // 保存对话记录
                    repository.saveUserConversation(
                        UserConversation(
                            userMessage = session.userPrompt ?: "AI推荐",
                            aiResponse = session.explanation,
                            type = ConversationType.RECOMMENDATION
                        )
                    )
                } else {
                    Toast.makeText(
                        this@AIRecommendationActivity,
                        "未找到推荐的歌曲，请尝试其他推荐",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@AIRecommendationActivity,
                    "播放失败：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showDeleteConfirmDialog(session: AIRecommendationSession) {
        AlertDialog.Builder(this)
            .setTitle("删除推荐")
            .setMessage("确定要删除这个推荐记录吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteAIRecommendation(session.id)
                    Toast.makeText(this@AIRecommendationActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearHistoryConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空历史")
            .setMessage("确定要清空所有推荐历史吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                lifecycleScope.launch {
                    repository.clearAIRecommendations()
                    Toast.makeText(this@AIRecommendationActivity, "已清空历史", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun shareRecommendation(session: AIRecommendationSession) {
        val shareText = buildString {
            appendLine("🎵 ${session.playlistName}")
            appendLine()
            appendLine(session.explanation)
            appendLine()
            appendLine("推荐歌曲：")
            session.songs.forEachIndexed { index, song ->
                appendLine("${index + 1}. ${song.songName} - ${song.artist}")
            }
            appendLine()
            appendLine("来自 MelodyFlow AI音乐推荐")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "分享推荐"))
    }

    override fun onResume() {
        super.onResume()
        checkAIConfig()
        loadUserStats()
    }
}

/**
 * AI推荐列表适配器
 */
class AIRecommendationAdapter(
    private val onPlayClick: (AIRecommendationSession) -> Unit,
    private val onDeleteClick: (AIRecommendationSession) -> Unit,
    private val onShareClick: (AIRecommendationSession) -> Unit
) : RecyclerView.Adapter<AIRecommendationAdapter.ViewHolder>() {

    private var items: List<AIRecommendationSession> = emptyList()
    private val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())

    fun submitList(newItems: List<AIRecommendationSession>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ai_recommendation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onPlayClick, onDeleteClick, onShareClick, dateFormat)
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPlaylistName: TextView = itemView.findViewById(R.id.tvPlaylistName)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvExplanation: TextView = itemView.findViewById(R.id.tvExplanation)
        private val songsContainer: ViewGroup = itemView.findViewById(R.id.songsContainer)
        private val tvSongCount: TextView = itemView.findViewById(R.id.tvSongCount)
        private val btnPlayAll: View = itemView.findViewById(R.id.btnPlayAll)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        private val btnShare: View = itemView.findViewById(R.id.btnShare)

        fun bind(
            session: AIRecommendationSession,
            onPlayClick: (AIRecommendationSession) -> Unit,
            onDeleteClick: (AIRecommendationSession) -> Unit,
            onShareClick: (AIRecommendationSession) -> Unit,
            dateFormat: SimpleDateFormat
        ) {
            tvPlaylistName.text = session.playlistName
            tvTimestamp.text = dateFormat.format(Date(session.timestamp))
            tvExplanation.text = session.explanation
            tvSongCount.text = "共 ${session.songs.size} 首歌曲"

            // 显示歌曲列表预览（带封面）
            songsContainer.removeAllViews()
            session.songs.take(5).forEach { song ->
                val songView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_recommended_song_preview, songsContainer, false)

                val ivCover = songView.findViewById<ImageView>(R.id.ivSongCover)
                val tvSongInfo = songView.findViewById<TextView>(R.id.tvSongInfo)

                tvSongInfo.text = "${song.songName} - ${song.artist}"

                // 使用默认音乐图标作为封面
                ivCover.setImageResource(R.drawable.ic_music_note)

                songsContainer.addView(songView)
            }

            if (session.songs.size > 5) {
                val moreView = TextView(itemView.context).apply {
                    text = "+ ${session.songs.size - 5} 首"
                    textSize = 13f
                    setTextColor(itemView.context.getColor(R.color.primary))
                    setPadding(48, 8, 0, 8)
                }
                songsContainer.addView(moreView)
            }

            btnPlayAll.setOnClickListener { onPlayClick(session) }
            btnDelete.setOnClickListener { onDeleteClick(session) }
            btnShare.setOnClickListener { onShareClick(session) }
        }
    }
}
