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
import com.bumptech.glide.Glide
import com.melodyflow.app.R
import com.melodyflow.app.model.Song

class ImportResultAdapter(
    private val onPlay: (Song) -> Unit,
    private val onFavorite: (Song, Boolean) -> Unit,
    private val favorites: Set<String> = emptySet()
) : ListAdapter<Song, ImportResultAdapter.ViewHolder>(DiffCallback()) {

    private var favoriteIds = favorites

    fun setFavorites(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)

        fun bind(song: Song, position: Int) {
            tvTitle.text = song.name
            tvArtist.text = song.artist

            // 优化图片加载：使用 thumbnail() 先加载小图，减少内存占用
            // 限制图片大小为 100x100，避免加载大图导致卡顿
            Glide.with(itemView.context)
                .load(song.getCoverUrl())
                .override(100, 100)  // 限制图片大小
                .centerCrop()
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .dontAnimate()  // 禁用动画，提升加载速度
                .into(ivCover)

            val isFav = favoriteIds.contains(song.id)
            btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_favorite
                else R.drawable.ic_favorite_border
            )

            itemView.setOnClickListener { onPlay(song) }
            btnFavorite.setOnClickListener {
                onFavorite(song, !isFav)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
    }
}
