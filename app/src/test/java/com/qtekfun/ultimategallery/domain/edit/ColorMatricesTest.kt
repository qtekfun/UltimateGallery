package com.qtekfun.ultimategallery.domain.edit

import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMatricesTest {
    private fun channels(argb: Int) = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 1) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= tolerance)
    }

    @Test
    fun untouchedEditIsTheIdentity() {
        assertTrue(ColorMatrices.isIdentity(ColorMatrices.forEdit(Adjustments.NONE, FilterChoice())))
        assertTrue(ColorMatrices.isIdentity(ColorMatrices.forFilter(FilterChoice(FilterPreset.SEPIA, 0f))))
    }

    @Test
    fun thenAppliesTheFirstMatrixFirst() {
        val color = 0xFF406080.toInt()
        val a = ColorMatrices.brightness(0.5f)
        val b = ColorMatrices.contrast(0.5f)
        val step = ColorMatrices.apply(b, ColorMatrices.apply(a, color))
        val composed = ColorMatrices.apply(ColorMatrices.then(a, b), color)
        channels(step).zip(channels(composed)).forEach { (s, c) -> assertNear(s, c) }
    }

    @Test
    fun fullDesaturationGivesGray() {
        val gray = ColorMatrices.apply(ColorMatrices.saturation(-1f), 0xFFFF0000.toInt())
        val (r, g, b) = channels(gray)
        assertEquals(r, g)
        assertEquals(g, b)
        assertNear(54, r)
    }

    @Test
    fun brightnessAndExposureLightenAndDarken() {
        val mid = 0xFF808080.toInt()
        assertTrue(channels(ColorMatrices.apply(ColorMatrices.brightness(0.5f), mid))[0] > 0x80)
        assertTrue(channels(ColorMatrices.apply(ColorMatrices.brightness(-0.5f), mid))[0] < 0x80)
        assertNear(0xFF, channels(ColorMatrices.apply(ColorMatrices.exposure(1f), mid))[0])
        assertTrue(channels(ColorMatrices.apply(ColorMatrices.exposure(-1f), mid))[0] < 0x40)
    }

    @Test
    fun contrastKeepsMiddleGray() {
        val mid = 0xFF808080.toInt()
        assertNear(0x80, channels(ColorMatrices.apply(ColorMatrices.contrast(0.8f), mid))[0])
        assertNear(0x80, channels(ColorMatrices.apply(ColorMatrices.contrast(-0.8f), mid))[0])
        assertTrue(channels(ColorMatrices.apply(ColorMatrices.contrast(0.8f), 0xFFC0C0C0.toInt()))[0] > 0xC0)
    }

    @Test
    fun warmthMovesRedAndBlueApart() {
        val (r, _, b) = channels(ColorMatrices.apply(ColorMatrices.warmth(1f), 0xFF808080.toInt()))
        assertTrue(r > 0x80 && b < 0x80)
        val (cr, _, cb) = channels(ColorMatrices.apply(ColorMatrices.warmth(-1f), 0xFF808080.toInt()))
        assertTrue(cr < 0x80 && cb > 0x80)
    }

    @Test
    fun blendInterpolatesBetweenIdentityAndPreset() {
        val sepia = ColorMatrices.forPreset(FilterPreset.SEPIA)
        val half = ColorMatrices.blend(sepia, 0.5f)
        val id = ColorMatrices.identity()
        half.indices.forEach { assertEquals((id[it] + sepia[it]) / 2f, half[it], 1e-6f) }
        assertArrayEquals(sepia, ColorMatrices.blend(sepia, 1f), 1e-6f)
    }

    @Test
    fun alphaIsNeverTouched() {
        FilterPreset.entries.forEach { preset ->
            val out = ColorMatrices.apply(ColorMatrices.forPreset(preset), 0x80406080.toInt())
            assertEquals(preset.name, 0x80, out ushr 24)
        }
    }
}
