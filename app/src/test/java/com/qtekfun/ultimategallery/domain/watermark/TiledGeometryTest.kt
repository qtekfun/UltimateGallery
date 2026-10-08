package com.qtekfun.ultimategallery.domain.watermark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiledGeometryTest {
    private fun centers(rotation: Float = 0f, stagger: Float = 0f, spacing: Float = 1f) =
        TiledGeometry.tileCenters(1000f, 500f, 100f, 50f, 500f, 250f, rotation, spacing, stagger, 0f)

    @Test
    fun includesTheAnchorTile() {
        assertTrue(centers().any { (x, y) -> x == 500f && y == 250f })
    }

    @Test
    fun unrotatedLatticeIsRegularWithTheGivenPitch() {
        val xs = centers().filter { it.second == 250f }.map { it.first }.sorted()
        assertEquals(200f, xs[1] - xs[0], 0.01f)
    }

    @Test
    fun staggerShiftsAlternateRows() {
        val rows = centers(stagger = 0.5f).groupBy { it.second }
        val anchorRow = rows.getValue(250f).map { it.first }
        val nextRow = rows.getValue(250f + 100f).map { it.first }
        assertTrue(anchorRow.contains(500f))
        assertTrue(nextRow.any { kotlin.math.abs(it - 600f) < 0.01f })
    }

    @Test
    fun rotatedLatticeStillCoversTheCorners() {
        val all = centers(rotation = 30f)
        listOf(0f to 0f, 1000f to 0f, 0f to 500f, 1000f to 500f).forEach { (cx, cy) ->
            assertTrue(all.any { (x, y) -> kotlin.math.hypot(x - cx, y - cy) < 150f })
        }
    }

    @Test
    fun tileCountIsCapped() {
        val many = TiledGeometry.tileCenters(100000f, 100000f, 10f, 10f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(TiledGeometry.MAX_TILES, many.size)
    }

    @Test
    fun degenerateMarkGivesNoTiles() {
        assertTrue(TiledGeometry.tileCenters(100f, 100f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f).isEmpty())
    }
}
