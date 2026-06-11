package com.melodyflow.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.melodyflow.app.databinding.ItemPlaylistSongBinding
import com.melodyflow.app.model.Song

class PlaylistAdapter(
    private val currentSongId: String?,
    private val onItemClick: (Song) -> Unit
) : ListAdapter<Song, PlaylistAdapter.ViewHolder>(SongDiffCallback()) {

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
            binding.tvPosition.text = "${position + 1}"
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
            binding.tvPosition.setTextColor(
                if (isCurrent) binding.root.context.getColor(com.melodyflow.app.R.color.primary)
                else binding.root.context.getColor(com.melodyflow.app.R.color.text_secondary)
            )
            binding.tvTitle.setTextColor(
                if (isCurrent) binding.root.context.getColor(com.melodyflow.app.R.color.primary)
                else binding.root.context.getColor(com.melodyflow.app.R.color.text_primary)
            )

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