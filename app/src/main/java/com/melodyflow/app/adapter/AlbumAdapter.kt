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
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.melodyflow.app.R
import com.melodyflow.app.model.Album

/**
 * Adapter for displaying album search results with album covers.
 */
class AlbumAdapter(
    private val onItemClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvArtist: TextView? = itemView.findViewById(R.id.tvArtist)

        fun bind(album: Album) {
            tvName.text = album.name
            tvArtist?.text = album.artist

            // Load album cover with rounded corners
            if (album.pic.startsWith("http://") || album.pic.startsWith("https://")) {
                Glide.with(itemView.context)
                    .load(album.pic)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .transform(
                        MultiTransformation(
                            CenterCrop(),
                            RoundedCorners(12)
                        )
                    )
                    .into(ivCover)
            } else {
                ivCover.setImageResource(R.drawable.ic_music_note)
            }

            itemView.setOnClickListener { onItemClick(album) }
        }
    }

    class AlbumDiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean =
            oldItem == newItem
    }
}
