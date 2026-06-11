package com.melodyflow.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.R
import com.melodyflow.app.adapter.LyricAdapter
import com.melodyflow.app.data.MusicRepository
import com.melodyflow.app.model.LyricLine
import com.melodyflow.app.util.LyricParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerLyricsFragment : Fragment() {
    private var rvLyrics: RecyclerView? = null
    private var tvNoLyrics: TextView? = null
    
    private var lyricAdapter: LyricAdapter? = null
    private var currentIndex = -1
    private var lyricsList = listOf<LyricLine>()
    private var isLoading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player_lyrics, container, false)
        rvLyrics = view.findViewById(R.id.rvLyrics)
        tvNoLyrics = view.findViewById(R.id.tvNoLyrics)

        lyricAdapter = LyricAdapter()
        rvLyrics?.adapter = lyricAdapter
        rvLyrics?.layoutManager = LinearLayoutManager(requireContext())

        // Show loading state initially
        showLoadingState()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rvLyrics = null
        tvNoLyrics = null
        lyricAdapter = null
    }

    private fun showLoadingState() {
        isLoading = true
        tvNoLyrics?.text = "歌词加载中..."
        tvNoLyrics?.visibility = View.VISIBLE
        rvLyrics?.visibility = View.GONE
    }

    private fun showEmptyState(message: String) {
        isLoading = false
        tvNoLyrics?.text = message
        tvNoLyrics?.visibility = View.VISIBLE
        rvLyrics?.visibility = View.GONE
    }

    private fun showLyricsContent() {
        isLoading = false
        rvLyrics?.visibility = View.VISIBLE
        tvNoLyrics?.visibility = View.GONE
    }

    fun loadLyrics(songId: String, repository: MusicRepository) {
        lifecycleScope.launch {
            try {
                showLoadingState()
                val lrcText = withContext(Dispatchers.IO) { repository.getLyrics(songId) }
                if (!lrcText.isNullOrBlank()) {
                    val parsed = LyricParser.parse(lrcText)
                    if (parsed.isNotEmpty()) {
                        lyricsList = parsed
                        lyricAdapter?.setLyrics(parsed)
                        showLyricsContent()
                        return@launch
                    }
                }
                // No lyrics available
                showEmptyState("暂无歌词")
            } catch (e: Exception) {
                showEmptyState("歌词加载失败")
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        currentIndex = index
        lyricAdapter?.setCurrentIndex(index)
        val rv = rvLyrics ?: return
        (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, rv.height / 3)
    }

    fun updatePosition(positionMs: Int) {
        if (lyricsList.isEmpty()) return
        var index = -1
        for (i in lyricsList.indices) {
            if (lyricsList[i].time > positionMs) {
                index = i - 1
                break
            }
        }
        if (index < 0 && positionMs >= lyricsList.last().time) {
            index = lyricsList.size - 1
        }
        if (index >= 0 && index != currentIndex) {
            setCurrentIndex(index)
        }
    }

    fun setLyrics(lyrics: List<LyricLine>) {
        if (lyrics.isNotEmpty()) {
            lyricAdapter?.setLyrics(lyrics)
            showLyricsContent()
        } else {
            showEmptyState("暂无歌词")
        }
    }
}
