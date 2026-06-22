package com.melodyflow.app.util

import com.melodyflow.app.model.LyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricParserTest {

    @Test
    fun parseStandardLRC() {
        val lrc = """
            [00:00.00]First line
            [00:05.50]Second line
            [00:10.00]Third line
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertEquals(3, result.size)
        assertEquals(LyricLine(0, "First line"), result[0])
        assertEquals(LyricLine(5500, "Second line"), result[1])
        assertEquals(LyricLine(10000, "Third line"), result[2])
    }

    @Test
    fun parseWithMilliseconds() {
        val lrc = """
            [00:00.00]Line 0
            [00:00.12]Line 120ms
            [00:00.50]Line 500ms
            [00:00.99]Line 990ms
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertEquals(4, result.size)
        assertEquals(LyricLine(0, "Line 0"), result[0])
        assertEquals(LyricLine(120, "Line 120ms"), result[1])
        assertEquals(LyricLine(500, "Line 500ms"), result[2])
        assertEquals(LyricLine(990, "Line 990ms"), result[3])
    }

    @Test
    fun parseWithTwoDigitMilliseconds() {
        // [00:00.12] should be parsed as 120ms (padEnd to 3 digits)
        val lrc = "[00:00.12]Test"
        val result = LyricParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals(120, result[0].time)
    }

    @Test
    fun parseWithThreeDigitMilliseconds() {
        val lrc = "[00:00.123]Test"
        val result = LyricParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals(123, result[0].time)
    }

    @Test
    fun parseEmptyText() {
        val result = LyricParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBlankText() {
        val result = LyricParser.parse("   \n  \n")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseInvalidFormat() {
        val lrc = """
            This is not LRC format
            [invalid]No match
            Just some text
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseMixedValidInvalid() {
        val lrc = """
            [00:00.00]Valid line
            This is garbage
            [00:05.00]Another valid line
            More garbage
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertEquals(2, result.size)
        assertEquals("Valid line", result[0].text)
        assertEquals("Another valid line", result[1].text)
    }

    @Test
    fun parseOrderedOutput() {
        // Lines given out of order should be sorted by time
        val lrc = """
            [00:10.00]Third
            [00:00.00]First
            [00:05.00]Second
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertEquals(3, result.size)
        assertEquals("First", result[0].text)
        assertEquals("Second", result[1].text)
        assertEquals("Third", result[2].text)
    }

    @Test
    fun parseEmptyLyricText() {
        // [00:00.00] with empty text should be skipped
        val lrc = """
            [00:00.00]
            [00:05.00]Valid
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("Valid", result[0].text)
    }

    @Test
    fun parseLargeLyrics() {
        val sb = StringBuilder()
        for (i in 0 until 200) {
            val min = i / 60
            val sec = i % 60
            sb.appendLine("[${String.format("%02d", min)}:${String.format("%02d", sec)}.00]Line $i")
        }
        val result = LyricParser.parse(sb.toString())
        assertEquals(200, result.size)
    }

    @Test
    fun parseSpecificTimeValues() {
        val lrc = """
            [01:30.00]One minute thirty
            [03:45.50]Three minutes forty-five point five
            [10:00.00]Ten minutes
        """.trimIndent()
        val result = LyricParser.parse(lrc)
        assertEquals(3, result.size)
        assertEquals(90000, result[0].time)
        assertEquals(225500, result[1].time)
        assertEquals(600000, result[2].time)
    }
}