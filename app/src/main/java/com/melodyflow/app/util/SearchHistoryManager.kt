package com.melodyflow.app.util

import android.content.Context
import androidx.core.content.edit

class SearchHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val maxHistory = 10

    fun getHistory(): List<String> {
        val count = prefs.getInt("count", 0)
        return (0 until count).mapNotNull { prefs.getString("item_$it", null) }
    }

    fun addQuery(query: String) {
        val history = getHistory().toMutableList()
        history.remove(query)
        history.add(0, query)
        while (history.size > maxHistory) {
            history.removeAt(history.size - 1)
        }
        prefs.edit {
            putInt("count", history.size)
            history.forEachIndexed { index, item ->
                putString("item_$index", item)
            }
        }
    }

    fun clearHistory() {
        prefs.edit { clear() }
    }
}