package com.melodyflow.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.R
import com.melodyflow.app.db.LocalSongEntity
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocalSongAdapter(
    private val onItemClick: (LocalSongEntity, Int) -> Unit
) : ListAdapter<LocalSongEntity, LocalSongAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val btnPlay: ImageButton = itemView.findViewById(R.id.btnPlay)

        fun bind(song: LocalSongEntity, position: Int) {
            tvTitle.text = song.title
            tvArtist.text = "${song.artist} - ${song.album}"
            tvDuration.text = formatDuration(song.duration)

            ivCover.setImageResource(R.drawable.ic_music_note)

            itemView.setOnClickListener { onItemClick(song, position) }
            btnPlay.setOnClickListener { onItemClick(song, position) }
        }

        private fun formatDuration(ms: Long): String {
            if (ms <= 0) return "--:--"
            val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LocalSongEntity>() {
        override fun areItemsTheSame(oldItem: LocalSongEntity, newItem: LocalSongEntity): Boolean =
            oldItem.filePath == newItem.filePath

        override fun areContentsTheSame(oldItem: LocalSongEntity, newItem: LocalSongEntity): Boolean =
            oldItem == newItem
    }
}
