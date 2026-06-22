package com.melodyflow.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.melodyflow.app.R
import com.melodyflow.app.databinding.ActivityCalendarConfigBinding

class MusicCalendarConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarConfigBinding
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        // Set result to canceled by default
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, resultValue)

        binding.btnConfirm.setOnClickListener {
            // Save theme preference
            saveWidgetConfig()
            // Update widget
            val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveWidgetConfig() {
        val themeMode = when (binding.themeGroup.checkedRadioButtonId) {
            R.id.radio_light -> "light"
            R.id.radio_dark -> "dark"
            else -> "system"
        }
        val prefs = getSharedPreferences("widget_config_$widgetId", MODE_PRIVATE)
        prefs.edit().putString("theme_mode", themeMode).apply()
    }
}