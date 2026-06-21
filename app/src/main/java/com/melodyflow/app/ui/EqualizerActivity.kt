package com.melodyflow.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.melodyflow.app.R

class EqualizerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_AUDIO_SESSION_ID = "audio_session_id"
        private const val ANIM_DURATION = 300L

        fun start(context: Context, audioSessionId: Int = 0) {
            val intent = Intent(context, EqualizerActivity::class.java).apply {
                putExtra(EXTRA_AUDIO_SESSION_ID, audioSessionId)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var spinnerPreset: Spinner
    private lateinit var switchEnable: SwitchMaterial
    private lateinit var layoutBands: LinearLayout
    private lateinit var sliderBass: Slider
    private lateinit var sliderVirtualizer: Slider
    private lateinit var tvBassValue: TextView
    private lateinit var tvVirtualizerValue: TextView
    private lateinit var tvBandRange: TextView
    private lateinit var cardEnable: MaterialCardView

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val bandSliders = mutableListOf<Slider>()
    private val presets = mutableListOf<String>()
    private val presetNamesMap = mutableMapOf<String, String>()
    private var currentPreset = 0

    private lateinit var prefs: SharedPreferences
    private var audioSessionId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

        audioSessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION_ID, 0)
        if (audioSessionId == 0) {
            android.util.Log.w("EqualizerActivity", "No valid audio session ID, using default (0)")
        }

        prefs = getSharedPreferences("MelodyFlow", MODE_PRIVATE)

        initViews()
        initAudioEffects()
        loadSettings()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        spinnerPreset = findViewById(R.id.spinnerPreset)
        switchEnable = findViewById(R.id.switchEnable)
        layoutBands = findViewById(R.id.layoutBands)
        sliderBass = findViewById(R.id.sliderBass)
        sliderVirtualizer = findViewById(R.id.sliderVirtualizer)
        tvBassValue = findViewById(R.id.tvBassValue)
        tvVirtualizerValue = findViewById(R.id.tvVirtualizerValue)
        tvBandRange = findViewById(R.id.tvBandRange)
        cardEnable = findViewById(R.id.cardEnable)

        toolbar.setNavigationOnClickListener { finish() }

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            setEqualizerEnabled(isChecked)
            prefs.edit().putBoolean("eq_enabled", isChecked).apply()
        }

        sliderBass.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setBassBoost(value.toInt())
                prefs.edit().putInt("eq_bass", value.toInt()).apply()
                tvBassValue.text = "${value.toInt() / 10}%"
            }
        }

        sliderVirtualizer.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setVirtualizer(value.toInt())
                prefs.edit().putInt("eq_virtualizer", value.toInt()).apply()
                tvVirtualizerValue.text = "${value.toInt() / 10}%"
            }
        }
    }

    private fun initAudioEffects() {
        try {
            val sessionId = if (audioSessionId != 0) audioSessionId else 0

            equalizer = try {
                Equalizer(0, sessionId)
            } catch (e: Exception) {
                android.util.Log.w("EqualizerActivity", "Failed to create Equalizer: ${e.message}")
                Toast.makeText(this, "无法初始化均衡器", Toast.LENGTH_SHORT).show()
                null
            }

            if (equalizer != null) {
                setupEqualizerBands()
            }

            try {
                bassBoost = BassBoost(0, sessionId)
                bassBoost?.enabled = false
            } catch (e: Exception) {
                android.util.Log.w("EqualizerActivity", "BassBoost not supported: ${e.message}")
            }

            try {
                virtualizer = Virtualizer(0, sessionId)
                virtualizer?.enabled = false
            } catch (e: Exception) {
                android.util.Log.w("EqualizerActivity", "Virtualizer not supported: ${e.message}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "均衡器功能暂不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEqualizerBands() {
        equalizer?.let { eq ->
            val numBands = eq.numberOfBands
            val range = eq.bandLevelRange
            val minLevel = range[0]
            val maxLevel = range[1]

            // Update band range text
            val firstFreq = formatFrequency(eq.getCenterFreq(0.toShort()) / 1000)
            val lastFreq = formatFrequency(eq.getCenterFreq((numBands - 1).toShort()) / 1000)
            tvBandRange.text = "${firstFreq} ~ ${lastFreq}"

            // 预设名称中英文映射
            val nameTranslation = mapOf(
                "Normal" to "标准",
                "Classical" to "古典",
                "Dance" to "舞曲",
                "Flat" to "平坦",
                "Folk" to "民谣",
                "Heavy Metal" to "重金属",
                "Hip Hop" to "嘻哈",
                "Jazz" to "爵士",
                "Pop" to "流行",
                "Rock" to "摇滚",
                "Bass Booster" to "低音增强",
                "Treble Booster" to "高音增强",
                "Vocal Booster" to "人声增强",
                "Acoustic" to "原声",
                "Country" to "乡村",
                "Latin" to "拉丁",
                "Party" to "派对",
                "Piano" to "钢琴",
                "R&B" to "节奏蓝调",
                "Ska" to "斯卡",
                "Soft Rock" to "轻摇滚",
                "Soul" to "灵魂",
                "Reggae" to "雷鬼",
                "Blues" to "布鲁斯",
                "Electronic" to "电子",
                "Default" to "默认",
                "Custom" to "自定义"
            )

            presets.clear()
            presetNamesMap.clear()
            presets.add("自定义")
            presetNamesMap["Custom"] = "自定义"

            for (i in 0 until eq.numberOfPresets) {
                val systemName = eq.getPresetName(i.toShort())
                val chineseName = nameTranslation[systemName] ?: systemName
                presets.add(chineseName)
                presetNamesMap[systemName] = chineseName
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPreset.adapter = adapter

            spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentPreset = position
                    if (position == 0) {
                        prefs.edit().putInt("eq_preset", position).apply()
                        return
                    }
                    val systemPresetIndex = (position - 1).toShort()
                    eq.usePreset(systemPresetIndex)
                    animatePresetChange()
                    prefs.edit().putInt("eq_preset", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            bandSliders.clear()
            layoutBands.removeAllViews()

            for (i in 0 until numBands) {
                val bandLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    gravity = android.view.Gravity.CENTER
                }

                val slider = Slider(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1f
                    )
                    stepSize = 1f
                    valueFrom = minLevel.toFloat()
                    valueTo = maxLevel.toFloat()
                    value = eq.getBandLevel(i.toShort()).toFloat()

                    addOnChangeListener { _, value, fromUser ->
                        if (fromUser) {
                            eq.setBandLevel(i.toShort(), value.toInt().toShort())
                            prefs.edit().putInt("eq_band_$i", value.toInt()).apply()
                            if (currentPreset != 0) {
                                spinnerPreset.setSelection(0)
                            }
                        }
                    }
                }

                // Apply custom slider style via code
                slider.setThumbRadius(resources.getDimensionPixelSize(R.dimen.corner_radius_small))
                slider.trackHeight = resources.getDimensionPixelSize(R.dimen.spacing_xs)

                val freqText = formatFrequency(eq.getCenterFreq(i.toShort()) / 1000)

                val label = TextView(this).apply {
                    text = freqText
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                }

                bandLayout.addView(label)
                bandLayout.addView(slider)
                layoutBands.addView(bandLayout)
                bandSliders.add(slider)
            }
        }
    }

    private fun formatFrequency(freq: Int): String {
        return if (freq >= 1000) {
            "${freq / 1000}K"
        } else {
            "$freq"
        }
    }

    /**
     * Animate all band sliders to their current equalizer levels smoothly
     */
    private fun animatePresetChange() {
        equalizer?.let { eq ->
            for (i in bandSliders.indices) {
                val targetValue = eq.getBandLevel(i.toShort()).toFloat()
                val currentValue = bandSliders[i].value
                if (currentValue != targetValue) {
                    animateSliderValue(bandSliders[i], currentValue, targetValue, ANIM_DURATION)
                }
            }
        }
    }

    /**
     * Smoothly animate a slider from start to end value using ValueAnimator
     */
    private fun animateSliderValue(slider: Slider, start: Float, end: Float, duration: Long) {
        val animator = ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                slider.value = animation.animatedValue as Float
            }
        }
        animator.start()
    }

    private fun updateBandLevels() {
        equalizer?.let { eq ->
            for (i in bandSliders.indices) {
                val level = eq.getBandLevel(i.toShort())
                bandSliders[i].value = level.toFloat()
            }
        }
    }

    private fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        sliderBass.isEnabled = enabled
        sliderVirtualizer.isEnabled = enabled
        bandSliders.forEach { it.isEnabled = enabled }
        spinnerPreset.isEnabled = enabled

        // Fade animation for the enable card
        val alpha = if (enabled) 1f else 0.5f
        cardEnable.animate()
            .alpha(alpha)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setBassBoost(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setVirtualizer(strength: Int) {
        try {
            virtualizer?.setStrength(strength.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSettings() {
        val enabled = prefs.getBoolean("eq_enabled", false)
        switchEnable.isChecked = enabled
        setEqualizerEnabled(enabled)

        val preset = prefs.getInt("eq_preset", 0)
        if (preset in presets.indices) {
            spinnerPreset.setSelection(preset)
        }

        val bass = prefs.getInt("eq_bass", 0)
        sliderBass.value = bass.toFloat()
        tvBassValue.text = "${bass / 10}%"

        val virtualizerStrength = prefs.getInt("eq_virtualizer", 0)
        sliderVirtualizer.value = virtualizerStrength.toFloat()
        tvVirtualizerValue.text = "${virtualizerStrength / 10}%"
    }

    override fun onDestroy() {
        super.onDestroy()
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
    }
}