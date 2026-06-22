package com.melodyflow.app.util

import com.melodyflow.app.model.LyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricsCacheTest {

    @Test
    fun cacheHit() {
        LyricsCache.clear()
        val lyrics = listOf(LyricLine(0, "Test"))
        LyricsCache.put("song1", lyrics)
        val cached = LyricsCache.get("song1")
        assertNotNull(cached)
        assertEquals(1, cached!!.size)
        assertEquals("Test", cached[0].text)
    }

    @Test
    fun cacheMiss() {
        LyricsCache.clear()
        val result = LyricsCache.get("nonexistent")
        assertNull(result)
    }

    @Test
    fun cachePutAndGet() {
        LyricsCache.clear()
        val lyrics = listOf(
            LyricLine(0, "Line 1"),
            LyricLine(5000, "Line 2"),
            LyricLine(10000, "Line 3")
        )
        LyricsCache.put("song1", lyrics)
        val cached = LyricsCache.get("song1")
        assertNotNull(cached)
        assertEquals(3, cached!!.size)
        assertEquals(5000, cached[1].time)
    }

    @Test
    fun cacheEviction() {
        LyricsCache.clear()
        // LRU cache has max size 20, add 25 entries
        for (i in 0 until 25) {
            LyricsCache.put("song$i", listOf(LyricLine(i.toLong(), "Line $i")))
        }
        // First 5 entries should be evicted
        assertNull(LyricsCache.get("song0"))
        assertNull(LyricsCache.get("song4"))
        // Later entries should still be present
        assertNotNull(LyricsCache.get("song20"))
        assertNotNull(LyricsCache.get("song24"))
    }

    @Test
    fun cacheClear() {
        LyricsCache.clear()
        LyricsCache.put("song1", listOf(LyricLine(0, "Test")))
        LyricsCache.clear()
        assertNull(LyricsCache.get("song1"))
    }

    @Test
    fun cacheOverwrite() {
        LyricsCache.clear()
        val original = listOf(LyricLine(0, "Original"))
        val updated = listOf(LyricLine(0, "Updated"), LyricLine(5000, "Extra"))
        LyricsCache.put("song1", original)
        assertEquals(1, LyricsCache.get("song1")!!.size)
        LyricsCache.put("song1", updated)
        assertEquals(2, LyricsCache.get("song1")!!.size)
        assertEquals("Updated", LyricsCache.get("song1")!![0].text)
    }

    @Test
    fun cacheEmptyList() {
        LyricsCache.clear()
        LyricsCache.put("song1", emptyList())
        val cached = LyricsCache.get("song1")
        assertNotNull(cached)
        assertTrue(cached!!.isEmpty())
    }
}