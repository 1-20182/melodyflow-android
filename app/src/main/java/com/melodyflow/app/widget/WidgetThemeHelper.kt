package com.melodyflow.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.melodyflow.app.R

object WidgetThemeHelper {

    enum class ThemeMode {
        SYSTEM, LIGHT, DARK
    }

    data class WidgetTheme(
        val isDark: Boolean,
        val accentColor: Int,
        val bgColor: Int,
        val textPrimaryColor: Int,
        val textSecondaryColor: Int
    )

    fun getTheme(context: Context, customAccentColor: Int? = null): WidgetTheme {
        val isDark = isDarkMode(context)
        val accent = customAccentColor ?: ContextCompat.getColor(context, R.color.widget_accent_default)
        return if (isDark) {
            WidgetTheme(
                isDark = true,
                accentColor = accent,
                bgColor = Color.parseColor("#1E1E1E"),
                textPrimaryColor = Color.parseColor("#E0E0E0"),
                textSecondaryColor = Color.parseColor("#9E9E9E")
            )
        } else {
            WidgetTheme(
                isDark = false,
                accentColor = accent,
                bgColor = Color.parseColor("#FFFFFF"),
                textPrimaryColor = Color.parseColor("#212121"),
                textSecondaryColor = Color.parseColor("#757575")
            )
        }
    }

    fun isDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    fun applyThemeToViews(views: RemoteViews, theme: WidgetTheme) {
        views.setTextColor(R.id.widget_month_title, theme.textPrimaryColor)
        views.setTextColor(R.id.widget_today_date, theme.textPrimaryColor)
        views.setTextColor(R.id.widget_today_schedule, theme.textSecondaryColor)
        views.setInt(R.id.widget_today_container, "setBackgroundColor", theme.bgColor)
    }
}