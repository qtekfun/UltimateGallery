package com.qtekfun.ultimategallery.domain.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMaskTest {
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFD02020.toInt()
    private val blue = 0xFF2020D0.toInt()

    /** A [size]² white photo with a red square in the middle half. */
    private fun product(size: Int = 40, background: Int = white): IntArray = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (x in size / 4 until 3 * size / 4 && y in size / 4 until 3 * size / 4) red else background
    }

    private fun alpha(mask: ByteArray, size: Int, x: Int, y: Int) = mask[y * size + x].toInt() and 0xFF

    @Test
    fun autoRemovesTheBorderBackgroundAndKeepsTheObject() {
        val mask = BackgroundMask.build(product(), 40, 40, listOf(MaskOp.Auto(0.2f)), feather = 0f)
        assertEquals(0, alpha(mask, 40, 0, 0))
        assertEquals(0, alpha(mask, 40, 5, 20))
        assertEquals(255, alpha(mask, 40, 20, 20))
    }

    @Test
    fun autoToleratesNoiseInTheBackground() {
        val noisy = product().mapIndexed { i, p -> if (p == white && i % 7 == 0) 0xFFF2F2F2.toInt() else p }.toIntArray()
        val mask = BackgroundMask.build(noisy, 40, 40, listOf(MaskOp.Auto(0.2f)), feather = 0f)
        assertEquals(0, alpha(mask, 40, 7, 0))
        assertEquals(255, alpha(mask, 40, 20, 20))
    }

    @Test
    fun seedRemovesOnlyTheConnectedSimilarRegion() {
        // Two separate blue blocks on red: a seed in the left one leaves the right one.
        val size = 30
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (y in 10 until 20 && (x in 2 until 10 || x in 20 until 28)) blue else red
        }
        val mask = BackgroundMask.build(pixels, size, size, listOf(MaskOp.Seed(EditPoint(5f / size, 15f / size), 0.1f)), feather = 0f)
        assertEquals(0, alpha(mask, size, 5, 15))
        assertEquals(255, alpha(mask, size, 25, 15))
        assertEquals(255, alpha(mask, size, 15, 15))
    }

    @Test
    fun brushesEraseAndRestore() {
        val ops = listOf(
            MaskOp.Auto(0.2f),
            MaskOp.Brush(listOf(EditPoint(0.5f, 0.1f), EditPoint(0.5f, 0.9f)), width = 0.1f, restore = false),
            MaskOp.Brush(listOf(EditPoint(0.1f, 0.1f)), width = 0.1f, restore = true)
        )
        val mask = BackgroundMask.build(product(), 40, 40, ops, feather = 0f)
        assertEquals(0, alpha(mask, 40, 20, 20))
        assertEquals(255, alpha(mask, 40, 4, 4))
        assertEquals(255, alpha(mask, 40, 12, 20))
    }

    @Test
    fun featherSoftensTheEdge() {
        val size = 400
        val mask = BackgroundMask.build(product(size), size, size, listOf(MaskOp.Auto(0.2f)), feather = 1f)
        val edge = alpha(mask, size, size / 4, size / 2)
        assertTrue("edge alpha $edge", edge in 1..254)
        assertEquals(0, alpha(mask, size, 5, 5))
        assertEquals(255, alpha(mask, size, size / 2, size / 2))
    }

    @Test
    fun applyMultipliesAlpha() {
        val pixels = intArrayOf(white, white, 0x80FFFFFF.toInt())
        BackgroundMask.applyTo(pixels, byteArrayOf(-1, 0, 0x80.toByte()))
        assertEquals(white, pixels[0])
        assertEquals(0x00FFFFFF, pixels[1])
        assertEquals(0x40, pixels[2] ushr 24)
    }

    @Test
    fun distanceIsZeroForEqualColorsAndLargeForOpposites() {
        assertEquals(0f, BackgroundMask.distance(red, red), 0f)
        assertTrue(BackgroundMask.distance(0xFF000000.toInt(), white) > 700f)
    }
}
