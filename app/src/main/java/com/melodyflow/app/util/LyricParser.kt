package com.melodyflow.app.util

import com.melodyflow.app.model.LyricLine

/**
 * Shared LRC lyrics parser utility.
 * Used by PlayerLyricsFragment, PlayerActivity (landscape lyrics), and any other component
 * that needs to parse LRC format lyrics.
 */
object LyricParser {

    private val LRC_REGEX = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    /**
     * Parse LRC format text into a sorted list of LyricLine.
     * Handles standard [mm:ss.xx] and [mm:ss.xxx] formats with millisecond precision.
     * Returns time-sorted list; invalid lines are skipped and logged.
     */
    fun parse(lrcText: String): List<LyricLine> {
        var parsedCount = 0
        var skippedCount = 0
        val lines = mutableListOf<LyricLine>()
        lrcText.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            LRC_REGEX.find(trimmed)?.let { match ->
                val minutes = match.groupValues[1].toLongOrNull()
                val seconds = match.groupValues[2].toLongOrNull()
                // Normalize millisecond string to 3 digits (e.g., "12" -> "120", "1" -> "100")
                val millisStr = match.groupValues[3].padEnd(3, '0').take(3)
                val millis = millisStr.toLongOrNull()
                val text = match.groupValues[4].trim()
                if (minutes != null && seconds != null && millis != null && text.isNotEmpty()) {
                    val totalMillis = minutes * 60000 + seconds * 1000 + millis
                    lines.add(LyricLine(totalMillis, text))
                    parsedCount++
                } else {
                    skippedCount++
                }
            } ?: run {
                // Line does not match LRC format; skip it silently
                skippedCount++
            }
        }
        Logger.d("LyricParser", "Parsed $parsedCount lines, skipped $skippedCount lines from ${lrcText.length} chars")
        if (lines.isEmpty()) {
            Logger.w("LyricParser", "No valid lyrics lines found in input")
        }
        return lines.sortedBy { it.time }
    }
}
