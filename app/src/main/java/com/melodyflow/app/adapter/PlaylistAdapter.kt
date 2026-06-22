package com.melodyflow.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.melodyflow.app.databinding.ItemPlaylistSongBinding
import com.melodyflow.app.model.Song

class PlaylistAdapter(
    private var currentSongId: String?,
    private val onItemClick: (Song) -> Unit
) : ListAdapter<Song, PlaylistAdapter.ViewHolder>(SongDiffCallback()) {

    fun updateCurrentSongId(songId: String?) {
        currentSongId = songId
        notifyDataSetChanged()
    }

    /**
     * 高效更新高亮位置，仅通知旧位置和新位置的 item 刷新，
     * 避免重建整个 Adapter 或全局刷新。
     */
    fun updateHighlight(newPosition: Int) {
        val oldPosition = currentList.indexOfFirst { it.id == currentSongId }
        currentSongId = if (newPosition in 0 until currentList.size) currentList[newPosition].id else null
        if (oldPosition >= 0) {
            notifyItemChanged(oldPosition)
        }
        if (newPosition >= 0 && newPosition < currentList.size) {
            notifyItemChanged(newPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(private val binding: ItemPlaylistSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, position: Int) {
            binding.tvTitle.text = song.name
            binding.tvArtist.text = song.artist

            Glide.with(binding.ivCover.context)
                .load(song.getCoverUrl())
                .placeholder(com.melodyflow.app.R.drawable.ic_music_note)
                .error(com.melodyflow.app.R.drawable.ic_music_note)
                .centerCrop()
                .into(binding.ivCover)

            // Highlight current playing song
            val isCurrent = song.id == currentSongId
            binding.root.isSelected = isCurrent

            if (isCurrent) {
                binding.tvPosition.visibility = View.GONE
                binding.ivPlayingIndicator.visibility = View.VISIBLE
                binding.tvTitle.setTextColor(binding.root.context.getColor(com.melodyflow.app.R.color.primary))
                binding.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.root.setBackgroundColor(binding.root.context.getColor(com.melodyflow.app.R.color.surface_variant))
            } else {
                binding.tvPosition.text = "${position + 1}"
                binding.tvPosition.visibility = View.VISIBLE
                binding.ivPlayingIndicator.visibility = View.GONE
                binding.tvTitle.setTextColor(binding.root.context.getColor(com.melodyflow.app.R.color.text_primary))
                binding.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.root.setBackgroundColor(binding.root.context.getColor(com.melodyflow.app.R.color.background))
            }

            binding.root.setOnClickListener { onItemClick(song) }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }
}