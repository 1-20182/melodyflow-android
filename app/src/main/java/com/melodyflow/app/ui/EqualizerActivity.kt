package com.melodyflow.app.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.melodyflow.app.R
import com.melodyflow.app.service.MusicService

class EqualizerActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, EqualizerActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var spinnerPreset: Spinner
    private lateinit var switchEnable: SwitchMaterial
    private lateinit var layoutBands: LinearLayout
    private lateinit var sliderBass: Slider
    private lateinit var sliderVirtualizer: Slider

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val bandSliders = mutableListOf<Slider>()
    private val presets = mutableListOf<String>()
    private var currentPreset = 0

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)

        com.melodyflow.app.util.BackgroundManager.applyToActivity(this)

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

        toolbar.setNavigationOnClickListener { finish() }

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            setEqualizerEnabled(isChecked)
            prefs.edit().putBoolean("eq_enabled", isChecked).apply()
        }

        sliderBass.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setBassBoost(value.toInt())
                prefs.edit().putInt("eq_bass", value.toInt()).apply()
            }
        }

        sliderVirtualizer.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setVirtualizer(value.toInt())
                prefs.edit().putInt("eq_virtualizer", value.toInt()).apply()
            }
        }
    }

    private fun initAudioEffects() {
        try {
            // 尝试从 MusicService 获取音频会话
            // 由于均衡器需要与具体的播放器关联，这里创建独立的均衡器
            // 在实际使用中，应该与 MusicService 的 AudioSession 关联
            
            // 创建一个虚拟的音频会话
            val audioSessionId = android.media.MediaPlayer::class.java
            // 注意：这里需要与实际播放器关联
            // 由于 MusicService 使用自己的 MediaPlayer，我们需要获取其 session ID
            
            // 暂时创建一个通用的均衡器
            // 这个实现需要在 MusicService 集成时进一步完善
            equalizer = try {
                Equalizer(0, 0)
            } catch (e: Exception) {
                Toast.makeText(this, "无法初始化均衡器", Toast.LENGTH_SHORT).show()
                null
            }

            if (equalizer != null) {
                setupEqualizerBands()
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

            // 获取预设
            presets.clear()
            for (i in 0 until eq.numberOfPresets) {
                presets.add(eq.getPresetName(i.toShort()))
            }

            // 设置 Spinner
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPreset.adapter = adapter

            spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentPreset = position
                    eq.usePreset(position.toShort())
                    updateBandLevels()
                    prefs.edit().putInt("eq_preset", position).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // 创建频段滑块
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
                            // 切换到自定义预设
                            if (currentPreset != 0) {
                                spinnerPreset.setSelection(0)
                            }
                        }
                    }
                }

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

            // 尝试初始化 BassBoost 和 Virtualizer
            try {
                bassBoost = BassBoost(0, 0)
                bassBoost?.enabled = switchEnable.isChecked
            } catch (e: Exception) {
                // 不支持
            }

            try {
                virtualizer = Virtualizer(0, 0)
                virtualizer?.enabled = switchEnable.isChecked
            } catch (e: Exception) {
                // 不支持
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

        val virtualizerStrength = prefs.getInt("eq_virtualizer", 0)
        sliderVirtualizer.value = virtualizerStrength.toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
    }
}