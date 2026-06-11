package com.melodyflow.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.melodyflow.app.R
import com.melodyflow.app.model.Chart

class ChartAdapter(
    private val onItemClick: (Chart) -> Unit
) : ListAdapter<Chart, ChartAdapter.ChartViewHolder>(ChartDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chart, parent, false)
        return ChartViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)

        fun bind(chart: Chart) {
            tvName.text = chart.name
            // Load cover art with placeholder and improved loading
            val coverUrl = chart.getCoverUrl()
            if (coverUrl.isNotBlank()) {
                Glide.with(itemView.context)
                    .load(coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .transform(
                        MultiTransformation(
                            CenterCrop(),
                            RoundedCorners(16)
                        )
                    )
                    .into(ivIcon)
            } else {
                ivIcon.setImageResource(R.drawable.ic_music_note)
                ivIcon.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.card_background)
                )
            }
            itemView.setOnClickListener { onItemClick(chart) }
        }
    }

    class ChartDiffCallback : DiffUtil.ItemCallback<Chart>() {
        override fun areItemsTheSame(oldItem: Chart, newItem: Chart): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Chart, newItem: Chart): Boolean =
            oldItem == newItem
    }
}
