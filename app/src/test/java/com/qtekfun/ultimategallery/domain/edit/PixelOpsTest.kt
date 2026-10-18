package com.qtekfun.ultimategallery.domain.edit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelOpsTest {
    @Test
    fun flatAreasAreUnchanged() {
        val flat = IntArray(25) { 0xFF808080.toInt() }
        assertArrayEquals(flat, PixelOps.sharpen(flat, 5, 5, 0, 5, 1f))
    }

    @Test
    fun edgesGainContrastAndAlphaIsKept() {
        // Left half dark, right half light.
        val rows = IntArray(36) { i -> if (i % 6 < 3) 0x80404040.toInt() else 0x80C0C0C0.toInt() }
        val out = PixelOps.sharpen(rows, 6, 6, 0, 6, 1f)
        val darkEdge = out[2 * 6 + 2]
        val lightEdge = out[2 * 6 + 3]
        assertTrue(darkEdge and 0xFF < 0x40)
        assertTrue(lightEdge and 0xFF > 0xC0)
        assertEquals(0x80, darkEdge ushr 24)
    }

    @Test
    fun bandsGiveTheSameResultAsTheWholePhoto() {
        val w = 7
        val h = 9
        val rows = IntArray(w * h) { i -> (0xFF shl 24) or ((i * 37) % 256 shl 16) or ((i * 11) % 256 shl 8) or ((i * 53) % 256) }
        val whole = PixelOps.sharpen(rows, w, h, 0, h, 0.7f)
        // Rows 3..6 computed from a band holding rows 2..7.
        val band = rows.copyOfRange(2 * w, 8 * w)
        val part = PixelOps.sharpen(band, w, 6, 1, 5, 0.7f)
        assertArrayEquals(whole.copyOfRange(3 * w, 7 * w), part)
    }
}
