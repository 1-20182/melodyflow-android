package com.melodyflow.app.adapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.melodyflow.app.R
import com.melodyflow.app.model.Song
import com.melodyflow.app.model.UnplayableSongsHolder

class SongAdapter(
    private val onItemClick: (Song, Int) -> Unit,
    private val onFavoriteClick: ((Song, Boolean) -> Unit)? = null,
    private val onCacheClick: ((Song) -> Unit)? = null,
    private val showCacheIndicator: Boolean = false
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private val favoriteIds = mutableSetOf<String>()
    private val cachedIds = mutableSetOf<String>()
    private val downloadingIds = mutableSetOf<String>()
    private val selectedIds = mutableSetOf<String>()
    private var onItemLongClick: ((Song) -> Unit)? = null
    private val unplayableListener = { refreshUnplayable() }

    fun setOnLongClickListener(listener: (Song) -> Unit) {
        onItemLongClick = listener
    }

    fun setSelectedIds(ids: Set<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun setFavorites(ids: Set<String>) {
        val oldIds = favoriteIds.toSet()
        favoriteIds.clear()
        favoriteIds.addAll(ids)
        if (oldIds != ids) {
            notifyDataSetChanged()
        }
    }

    fun setCached(ids: Set<String>) {
        val oldIds = cachedIds.toSet()
        cachedIds.clear()
        cachedIds.addAll(ids)
        if (oldIds != ids) {
            notifyDataSetChanged()
        }
    }

    fun setDownloading(ids: Set<String>) {
        val oldIds = downloadingIds.toSet()
        downloadingIds.clear()
        downloadingIds.addAll(ids)
        if (oldIds != ids) {
            notifyDataSetChanged()
        }
    }

    fun refreshUnplayable() {
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        UnplayableSongsHolder.addListener(unplayableListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        UnplayableSongsHolder.removeListener(unplayableListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
        holder.itemView.animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.fade_in_up)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("favorite_update")) {
            val song = getItem(position)
            val isFav = favoriteIds.contains(song.id)
            holder.updateFavoriteState(isFav, animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        private val ivCached: ImageView = itemView.findViewById(R.id.ivCached)
        private val ivCacheStatus: ImageView? = itemView.findViewById(R.id.ivCacheStatus)
        private val btnCache: ImageButton = itemView.findViewById(R.id.btnCache)

        fun bind(song: Song, position: Int) {
            tvTitle.text = song.name
            tvArtist.text = song.artist

            val isCached = cachedIds.contains(song.id)
            val isDownloading = downloadingIds.contains(song.id)
            val isUnplayable = !isCached && UnplayableSongsHolder.contains(song.id)

            // Apply grayscale filter for unplayable songs
            if (isUnplayable) {
                tvTitle.alpha = 0.4f
                tvArtist.alpha = 0.4f
                ivCover.alpha = 0.5f
                ivCover.colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply { setSaturation(0f) }
                )
            } else {
                tvTitle.alpha = 1.0f
                tvArtist.alpha = 1.0f
                ivCover.alpha = 1.0f
                ivCover.clearColorFilter()
            }

            if (showCacheIndicator) {
                ivCached.visibility = View.VISIBLE
                ivCached.setImageResource(if (isCached) R.drawable.ic_cache else R.drawable.ic_music_note)
                ivCached.setColorFilter(
                    if (isCached) itemView.context.getColor(R.color.primary)
                    else itemView.context.getColor(R.color.text_disabled)
                )
            } else {
                ivCached.visibility = View.GONE
            }

            // Update cache status indicator (small icon next to title)
            ivCacheStatus?.let { indicator ->
                if (isCached) {
                    indicator.visibility = View.VISIBLE
                    indicator.setImageResource(R.drawable.ic_cache)
                    indicator.setColorFilter(itemView.context.getColor(R.color.primary))
                } else {
                    indicator.visibility = View.GONE
                }
            }

            // Update cache button state
            when {
                isCached -> {
                    btnCache.setImageResource(R.drawable.ic_cache)
                    btnCache.setColorFilter(itemView.context.getColor(R.color.primary))
                    btnCache.isEnabled = false
                    btnCache.alpha = 0.6f
                }
                isDownloading -> {
                    btnCache.setImageResource(R.drawable.ic_downloading)
                    btnCache.setColorFilter(itemView.context.getColor(R.color.primary))
                    btnCache.isEnabled = false
                    btnCache.alpha = 1.0f
                    // Add rotation animation
                    val rotateAnimation = android.view.animation.AnimationUtils.loadAnimation(itemView.context, R.anim.rotate_playing)
                    btnCache.startAnimation(rotateAnimation)
                }
                else -> {
                    btnCache.setImageResource(R.drawable.ic_download)
                    btnCache.setColorFilter(itemView.context.getColor(R.color.text_secondary))
                    btnCache.isEnabled = true
                    btnCache.alpha = 1.0f
                    btnCache.clearAnimation()
                }
            }

            Glide.with(itemView.context)
                .load(song.getCoverUrl())
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .centerCrop()
                .into(ivCover)

            val isFav = favoriteIds.contains(song.id)
            updateFavoriteState(isFav, animate = false)

            itemView.setOnClickListener {
                if (!UnplayableSongsHolder.contains(song.id) || isCached) {
                    animateButtonPress(itemView)
                    onItemClick(song, position)
                }
            }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(song)
                true
            }

            // Show selected state
            val isSelected = selectedIds.contains(song.id)
            if (isSelected) {
                itemView.alpha = 0.7f
                itemView.setBackgroundColor(itemView.context.getColor(R.color.surface_variant))
            } else {
                itemView.alpha = 1.0f
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            btnFavorite.setOnClickListener {
                animateButtonPress(btnFavorite)
                onFavoriteClick?.invoke(song, !isFav)
            }
            btnCache.setOnClickListener {
                if (!isCached && !isDownloading) {
                    animateButtonPress(btnCache)
                    onCacheClick?.invoke(song)
                }
            }
        }

        fun updateFavoriteState(isFav: Boolean, animate: Boolean = true) {
            if (animate) {
                if (isFav) {
                    animateFavoriteAdd()
                } else {
                    animateFavoriteRemove()
                }
            }
            btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            btnFavorite.setColorFilter(
                if (isFav) itemView.context.getColor(R.color.primary)
                else itemView.context.getColor(R.color.text_secondary)
            )
        }

        private fun animateButtonPress(view: View) {
            val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f).apply {
                duration = 100
                interpolator = DecelerateInterpolator()
            }
            val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f).apply {
                duration = 100
                interpolator = DecelerateInterpolator()
            }
            val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).apply {
                duration = 150
                interpolator = OvershootInterpolator(1.3f)
                startDelay = 100
            }
            val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).apply {
                duration = 150
                interpolator = OvershootInterpolator(1.3f)
                startDelay = 100
            }
            AnimatorSet().apply {
                playTogether(scaleDownX, scaleDownY)
                playTogether(scaleUpX, scaleUpY)
                start()
            }
        }

        private fun animateFavoriteAdd() {
            val scaleUpX = ObjectAnimator.ofFloat(btnFavorite, "scaleX", 1f, 1.3f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
            }
            val scaleUpY = ObjectAnimator.ofFloat(btnFavorite, "scaleY", 1f, 1.3f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
            }
            val scaleDownX = ObjectAnimator.ofFloat(btnFavorite, "scaleX", 1.3f, 1f).apply {
                duration = 200
                interpolator = OvershootInterpolator(1.1f)
                startDelay = 150
            }
            val scaleDownY = ObjectAnimator.ofFloat(btnFavorite, "scaleY", 1.3f, 1f).apply {
                duration = 200
                interpolator = OvershootInterpolator(1.1f)
                startDelay = 150
            }
            AnimatorSet().apply {
                playTogether(scaleUpX, scaleUpY)
                playTogether(scaleDownX, scaleDownY)
                start()
            }
        }

        private fun animateFavoriteRemove() {
            val scaleDownX = ObjectAnimator.ofFloat(btnFavorite, "scaleX", 1f, 0.8f).apply {
                duration = 100
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleDownY = ObjectAnimator.ofFloat(btnFavorite, "scaleY", 1f, 0.8f).apply {
                duration = 100
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleUpX = ObjectAnimator.ofFloat(btnFavorite, "scaleX", 0.8f, 1f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                startDelay = 100
            }
            val scaleUpY = ObjectAnimator.ofFloat(btnFavorite, "scaleY", 0.8f, 1f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                startDelay = 100
            }
            AnimatorSet().apply {
                playTogether(scaleDownX, scaleDownY)
                playTogether(scaleUpX, scaleUpY)
                start()
            }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }
}