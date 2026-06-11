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
     * Handles standard [mm:ss.xx] and [mm:ss.xxx] formats.
     */
    fun parse(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        lrcText.lines().forEach { line ->
            LRC_REGEX.find(line)?.let { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@let
                val seconds = match.groupValues[2].toLongOrNull() ?: return@let
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    val millis = (minutes * 60 + seconds) * 1000
                    lines.add(LyricLine(millis, text))
                }
            }
        }
        return lines.sortedBy { it.time }
    }
}
