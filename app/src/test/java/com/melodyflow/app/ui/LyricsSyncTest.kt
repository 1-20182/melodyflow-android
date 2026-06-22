package com.melodyflow.app.ui

import com.melodyflow.app.model.LyricLine
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the binary search lyrics index finding logic.
 * Mirror of the findLyricIndexByPosition method in PlayerActivity.
 */
class LyricsSyncTest {

    /**
     * Binary search: find the first index where lyrics[i].time > position,
     * then return maxOf(0, i - 1).
     */
    private fun findLyricIndexByPosition(lyrics: List<LyricLine>, position: Int): Int {
        if (lyrics.isEmpty()) return -1
        var left = 0
        var right = lyrics.size
        while (left < right) {
            val mid = (left + right) / 2
            if (lyrics[mid].time <= position) {
                left = mid + 1
            } else {
                right = mid
            }
        }
        return maxOf(0, left - 1)
    }

    private val sampleLyrics = listOf(
        LyricLine(0, "Line 0"),
        LyricLine(5000, "Line 1"),
        LyricLine(10000, "Line 2"),
        LyricLine(15000, "Line 3"),
        LyricLine(20000, "Line 4")
    )

    @Test
    fun findLyricIndex_binarySearch() {
        assertEquals(0, findLyricIndexByPosition(sampleLyrics, 0))
        assertEquals(0, findLyricIndexByPosition(sampleLyrics, 1000))
        assertEquals(0, findLyricIndexByPosition(sampleLyrics, 4999))
        assertEquals(1, findLyricIndexByPosition(sampleLyrics, 5000))
        assertEquals(1, findLyricIndexByPosition(sampleLyrics, 7500))
        assertEquals(2, findLyricIndexByPosition(sampleLyrics, 10000))
        assertEquals(2, findLyricIndexByPosition(sampleLyrics, 12500))
        assertEquals(3, findLyricIndexByPosition(sampleLyrics, 15000))
        assertEquals(4, findLyricIndexByPosition(sampleLyrics, 20000))
        assertEquals(4, findLyricIndexByPosition(sampleLyrics, 99999))
    }

    @Test
    fun findLyricIndex_emptyList() {
        assertEquals(-1, findLyricIndexByPosition(emptyList(), 5000))
    }

    @Test
    fun findLyricIndex_singleElement() {
        val lyrics = listOf(LyricLine(10000, "Only"))
        assertEquals(0, findLyricIndexByPosition(lyrics, 0))
        assertEquals(0, findLyricIndexByPosition(lyrics, 5000))
        assertEquals(0, findLyricIndexByPosition(lyrics, 10000))
        assertEquals(0, findLyricIndexByPosition(lyrics, 99999))
    }

    @Test
    fun findLyricIndex_boundaryConditions() {
        val lyrics = listOf(
            LyricLine(1000, "A"),
            LyricLine(2000, "B"),
            LyricLine(3000, "C")
        )
        // Before first lyric
        assertEquals(0, findLyricIndexByPosition(lyrics, 0))
        assertEquals(0, findLyricIndexByPosition(lyrics, 500))
        // At exact boundaries
        assertEquals(0, findLyricIndexByPosition(lyrics, 1000))
        assertEquals(1, findLyricIndexByPosition(lyrics, 2000))
        assertEquals(2, findLyricIndexByPosition(lyrics, 3000))
        // After last lyric
        assertEquals(2, findLyricIndexByPosition(lyrics, 5000))
    }

    @Test
    fun findLyricIndex_largeLyricSet() {
        val lyrics = mutableListOf<LyricLine>()
        for (i in 0 until 200) {
            lyrics.add(LyricLine((i * 3000).toLong(), "Line $i"))
        }
        assertEquals(0, findLyricIndexByPosition(lyrics, 0))
        assertEquals(0, findLyricIndexByPosition(lyrics, 2999))
        assertEquals(1, findLyricIndexByPosition(lyrics, 3000))
        assertEquals(50, findLyricIndexByPosition(lyrics, 150000))
        assertEquals(100, findLyricIndexByPosition(lyrics, 300000))
        assertEquals(199, findLyricIndexByPosition(lyrics, 999999))
    }
}