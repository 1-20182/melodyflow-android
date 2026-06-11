package com.melodyflow.app.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.melodyflow.app.R
import com.melodyflow.app.model.Song

class PlayerCoverFragment : Fragment() {
    private var song: Song? = null
    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var coverContainer: View
    private lateinit var coverPlayOverlay: View
    private lateinit var btnCoverPlay: ImageButton
    private var rotationAnimator: ValueAnimator? = null
    private var isPlaying = false

    interface OnCoverPlayListener {
        fun onCoverPlayClick()
    }

    private var coverPlayListener: OnCoverPlayListener? = null

    fun setOnCoverPlayListener(listener: OnCoverPlayListener) {
        coverPlayListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_player_cover, container, false)

        ivCover = view.findViewById(R.id.ivCover)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvArtist = view.findViewById(R.id.tvArtist)
        coverContainer = view.findViewById(R.id.coverContainer)
        coverPlayOverlay = view.findViewById(R.id.coverPlayOverlay)
        btnCoverPlay = view.findViewById(R.id.btnCoverPlay)

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 16000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                coverContainer.rotation = value
            }
        }

        coverContainer.setOnClickListener {
            showCoverPlayOverlay()
        }

        btnCoverPlay.setOnClickListener {
            coverPlayListener?.onCoverPlayClick()
            hideCoverPlayOverlay()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        android.util.Log.i("PlayerCover", "onViewCreated called")
        song?.let {
            android.util.Log.i("PlayerCover", "Updating cover for: ${it.name}")
            updateSongView(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun showCoverPlayOverlay() {
        coverPlayOverlay.alpha = 0f
        coverPlayOverlay.visibility = View.VISIBLE

        val fadeIn = android.animation.ObjectAnimator.ofFloat(coverPlayOverlay, "alpha", 0f, 1f)
        fadeIn.duration = 200
        fadeIn.interpolator = android.view.animation.DecelerateInterpolator()
        fadeIn.start()

        view?.postDelayed({
            hideCoverPlayOverlay()
        }, 1500)
    }

    private fun hideCoverPlayOverlay() {
        val fadeOut = android.animation.ObjectAnimator.ofFloat(coverPlayOverlay, "alpha", 1f, 0f)
        fadeOut.duration = 200
        fadeOut.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                coverPlayOverlay.visibility = View.GONE
            }
        })
        fadeOut.start()
    }

    fun updateSongInfo(s: Song) {
        android.util.Log.i("PlayerCover", "updateSongInfo: ${s.name}, isAdded=${isAdded}")
        song = s
        if (isAdded) {
            updateSongView(s)
        }
    }

    fun updateCoverAnimation(playing: Boolean) {
        isPlaying = playing
        if (playing) {
            startRotation()
        } else {
            pauseRotation()
        }
        btnCoverPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun startRotation() {
        if (rotationAnimator?.isRunning == false) {
            rotationAnimator?.start()
        } else if (rotationAnimator?.isPaused == true) {
            rotationAnimator?.resume()
        }
    }

    private fun pauseRotation() {
        rotationAnimator?.pause()
    }

    fun stopRotation() {
        rotationAnimator?.cancel()
    }

    private fun updateSongView(s: Song) {
        tvTitle.text = s.name
        tvArtist.text = s.artist
        
        context?.let { ctx ->
            try {
                Glide.with(ctx)
                    .load(s.getCoverUrl())
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .circleCrop()
                    .into(ivCover)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}