package com.melodyflow.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.R
import com.melodyflow.app.adapter.PlaylistAdapter
import com.melodyflow.app.model.Song
import com.melodyflow.app.service.MusicService

class PlaylistActivity : AppCompatActivity() {

    private lateinit var rvPlaylist: RecyclerView
    private lateinit var emptyLayout: View
    private lateinit var adapter: PlaylistAdapter

    private var musicService: MusicService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.LocalBinder
            musicService = binder.getService()
            serviceBound = true
            updatePlaylist()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        setupToolbar()
        setupRecyclerView()
        bindMusicService()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        rvPlaylist = findViewById(R.id.rvPlaylist)
        emptyLayout = findViewById(R.id.emptyLayout)

        adapter = PlaylistAdapter(null) { song ->
            musicService?.playSong(song)
        }

        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvPlaylist.adapter = adapter
        rvPlaylist.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updatePlaylist() {
        val playlist = musicService?.getPlaylist() ?: emptyList()
        val currentSongId = musicService?.getCurrentSong()?.id

        if (playlist.isEmpty()) {
            rvPlaylist.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
            title = "播放列表"
        } else {
            rvPlaylist.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
            title = "播放列表 (${playlist.size})"

            adapter = PlaylistAdapter(currentSongId) { song ->
                musicService?.playSong(song)
                adapter.updateCurrentSongId(song.id)
                scrollToCurrentSong(playlist, song.id)
            }
            rvPlaylist.adapter = adapter
            adapter.submitList(playlist)

            // 滚动到当前播放歌曲并居中
            scrollToCurrentSong(playlist, currentSongId)

            // 设置列表滚动监听，确保当前歌曲在屏幕变化时居中
            rvPlaylist.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val currentId = musicService?.getCurrentSong()?.id
                        if (currentId != null) {
                            val pos = playlist.indexOfFirst { it.id == currentId }
                            if (pos != -1) {
                                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                val lastVisible = layoutManager.findLastVisibleItemPosition()
                                if (pos < firstVisible || pos > lastVisible) {
                                    smoothScrollToCenter(pos)
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    private fun scrollToCurrentSong(playlist: List<Song>, currentSongId: String?) {
        val position = playlist.indexOfFirst { it.id == currentSongId }
        if (position != -1) {
            rvPlaylist.post {
                val layoutManager = rvPlaylist.layoutManager as? LinearLayoutManager
                layoutManager?.let {
                    // 使用 smoothScrollToPosition 让歌曲位于中间
                    smoothScrollToCenter(position)
                }
            }
        }
    }

    private fun smoothScrollToCenter(position: Int) {
        val layoutManager = rvPlaylist.layoutManager as LinearLayoutManager
        val smoothScroller = CenterSmoothScroller(this)
        smoothScroller.targetPosition = position
        layoutManager.startSmoothScroll(smoothScroller)
    }

    override fun onResume() {
        super.onResume()
        if (serviceBound) {
            updatePlaylist()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PlaylistActivity::class.java))
        }
    }
}

/**
 * 将目标Item滚动到RecyclerView中间的SmoothScroller
 */
class CenterSmoothScroller(context: Context) : androidx.recyclerview.widget.LinearSmoothScroller(context) {

    override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
    ): Int {
        // 让目标Item居中显示
        return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
    }
}
