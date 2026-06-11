package com.melodyflow.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.melodyflow.app.R
import com.melodyflow.app.model.Artist

/**
 * Adapter for displaying artist search results with circular avatars.
 */
class ArtistAdapter(
    private val onItemClick: (Artist) -> Unit
) : ListAdapter<Artist, ArtistAdapter.ArtistViewHolder>(ArtistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_artist, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)

        fun bind(artist: Artist) {
            tvName.text = artist.name

            // Load circular avatar
            if (artist.pic.startsWith("http://") || artist.pic.startsWith("https://")) {
                Glide.with(itemView.context)
                    .load(artist.pic)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .apply(RequestOptions.circleCropTransform())
                    .into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_music_note)
            }

            itemView.setOnClickListener { onItemClick(artist) }
        }
    }

    class ArtistDiffCallback : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean =
            oldItem == newItem
    }
}
