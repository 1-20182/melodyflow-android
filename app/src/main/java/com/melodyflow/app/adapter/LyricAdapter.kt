package com.melodyflow.app.adapter

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.melodyflow.app.R
import com.melodyflow.app.model.LyricLine

class LyricAdapter : RecyclerView.Adapter<LyricAdapter.LyricViewHolder>() {

    private val lyrics = mutableListOf<LyricLine>()
    private var currentIndex = -1

    // Reusable animator set to avoid excessive object creation
    private var colorAnimator: ValueAnimator? = null
    private var sizeAnimator: ValueAnimator? = null
    private var alphaAnimator: ValueAnimator? = null

    companion object {
        // Pre-computed colors
        val COLOR_ACTIVE = Color.parseColor("#00FF7F")  // 亮绿色
        val COLOR_PAST = Color.parseColor("#888888")
        val COLOR_FUTURE = Color.parseColor("#CCCCCC")
        val COLOR_ACTIVE_BG = Color.parseColor("#1A00FF7F")
        const val SIZE_ACTIVE = 18f
        const val SIZE_INACTIVE = 14f
        const val ALPHA_PAST = 0.6f
        const val ALPHA_FUTURE = 0.4f
        const val ANIM_DURATION = 300L
        val INTERPOLATOR = AccelerateDecelerateInterpolator()
    }

    fun setLyrics(newLyrics: List<LyricLine>) {
        lyrics.clear()
        lyrics.addAll(newLyrics)
        currentIndex = -1
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        if (currentIndex != index) {
            val oldIndex = currentIndex
            currentIndex = index
            if (oldIndex >= 0) notifyItemChanged(oldIndex, "update")
            if (index >= 0) notifyItemChanged(index, "update")
        }
    }

    fun getCurrentIndex(): Int = currentIndex

    fun updatePosition(position: Long) {
        val index = lyrics.indexOfLast { it.time <= position }
        if (index != currentIndex) {
            setCurrentIndex(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lyric, parent, false)
        return LyricViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        holder.bind(lyrics[position], position == currentIndex)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val isCurrent = position == currentIndex
            animateLyricChange(holder.tvLyric, position, isCurrent)
        }
    }

    override fun getItemCount(): Int = lyrics.size

    private fun animateLyricChange(tvLyric: TextView, position: Int, isCurrent: Boolean) {
        val context = tvLyric.context

        // Cancel any existing animators to prevent stacking
        colorAnimator?.cancel()
        sizeAnimator?.cancel()
        alphaAnimator?.cancel()

        val targetColor: Int
        val targetSize: Float
        val targetAlpha: Float
        val hasBackground: Boolean

        if (isCurrent) {
            targetColor = COLOR_ACTIVE
            targetSize = SIZE_ACTIVE
            targetAlpha = 1f
            hasBackground = true
        } else if (position < currentIndex) {
            targetColor = COLOR_PAST
            targetSize = SIZE_INACTIVE
            targetAlpha = ALPHA_PAST
            hasBackground = false
        } else {
            targetColor = COLOR_FUTURE
            targetSize = SIZE_INACTIVE
            targetAlpha = ALPHA_FUTURE
            hasBackground = false
        }

        // Animate text color
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), tvLyric.currentTextColor, targetColor).apply {
            duration = ANIM_DURATION
            interpolator = INTERPOLATOR
            addUpdateListener { animator ->
                tvLyric.setTextColor(animator.animatedValue as Int)
            }
            start()
        }

        // Animate text size
        val currentSize = tvLyric.textSize / context.resources.displayMetrics.scaledDensity
        sizeAnimator = ValueAnimator.ofFloat(currentSize, targetSize).apply {
            duration = ANIM_DURATION
            interpolator = INTERPOLATOR
            addUpdateListener { animator ->
                tvLyric.textSize = animator.animatedValue as Float
            }
            start()
        }

        // Animate alpha
        alphaAnimator = ValueAnimator.ofFloat(tvLyric.alpha, targetAlpha).apply {
            duration = ANIM_DURATION
            interpolator = INTERPOLATOR
            addUpdateListener { animator ->
                tvLyric.alpha = animator.animatedValue as Float
            }
            start()
        }

        // Handle typeface and background immediately
        if (isCurrent) {
            tvLyric.setTypeface(null, Typeface.BOLD)
            tvLyric.background = createRoundedBackground(context)
            tvLyric.setPadding(
                dpToPx(8f, context).toInt(),
                dpToPx(4f, context).toInt(),
                dpToPx(8f, context).toInt(),
                dpToPx(4f, context).toInt()
            )
        } else {
            tvLyric.setTypeface(null, Typeface.NORMAL)
            tvLyric.background = null
            tvLyric.setPadding(0, 0, 0, 0)
        }
    }

    private fun createRoundedBackground(context: android.content.Context): GradientDrawable {
        val radiusPx = dpToPx(8f, context)
        return GradientDrawable().apply {
            setColor(COLOR_ACTIVE_BG)
            cornerRadius = radiusPx
        }
    }

    private fun dpToPx(dp: Float, context: android.content.Context): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        )
    }

    inner class LyricViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLyric: TextView = itemView.findViewById(R.id.tvLyric)

        fun bind(lyric: LyricLine, isCurrent: Boolean) {
            tvLyric.text = lyric.text
            tvLyric.elevation = 0f
            tvLyric.setTypeface(null, Typeface.NORMAL)
            tvLyric.background = null
            tvLyric.setPadding(0, 0, 0, 0)

            if (isCurrent) {
                // Current lyric: green, bold, 18sp, green rounded background
                tvLyric.setTextColor(COLOR_ACTIVE)
                tvLyric.textSize = SIZE_ACTIVE
                tvLyric.setTypeface(null, Typeface.BOLD)
                tvLyric.alpha = 1f
                tvLyric.background = createRoundedBackground(itemView.context)
                tvLyric.setPadding(
                    dpToPx(8f, itemView.context).toInt(),
                    dpToPx(4f, itemView.context).toInt(),
                    dpToPx(8f, itemView.context).toInt(),
                    dpToPx(4f, itemView.context).toInt()
                )
            } else {
                val pos = adapterPosition
                if (pos >= 0 && pos < this@LyricAdapter.currentIndex) {
                    // Past lyric: gray, 14sp, alpha 0.6
                    tvLyric.setTextColor(COLOR_PAST)
                    tvLyric.textSize = SIZE_INACTIVE
                    tvLyric.alpha = ALPHA_PAST
                } else {
                    // Future lyric: gray, 14sp, alpha 0.4
                    tvLyric.setTextColor(COLOR_FUTURE)
                    tvLyric.textSize = SIZE_INACTIVE
                    tvLyric.alpha = ALPHA_FUTURE
                }
            }
        }
    }
}
