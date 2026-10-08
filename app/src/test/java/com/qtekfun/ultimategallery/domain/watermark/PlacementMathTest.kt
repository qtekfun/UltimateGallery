package com.qtekfun.ultimategallery.domain.watermark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementMathTest {
    private val delta = 1e-3f

    @Test
    fun orientationBucketsFollowTheDisplayedSize() {
        assertEquals(Orientation.PORTRAIT, Orientation.of(3000, 4000))
        assertEquals(Orientation.LANDSCAPE, Orientation.of(4000, 3000))
    }

    @Test
    fun squareImagesCountAsLandscape() {
        assertEquals(Orientation.LANDSCAPE, Orientation.of(1000, 1000))
    }

    @Test
    fun profileKeepsIndependentPlacementsPerOrientation() {
        val p = WatermarkProfile()
        val moved = p.withPlacement(Orientation.PORTRAIT, Placement(0.1f, 0.2f, 0.3f, 10f))
        assertEquals(Placement(0.1f, 0.2f, 0.3f, 10f), moved.placementFor(Orientation.PORTRAIT))
        assertEquals(p.landscape, moved.placementFor(Orientation.LANDSCAPE))
        assertEquals(p.landscape, moved.landscape)
    }

    @Test
    fun placementStaysTheSameFractionAtAnyResolution() {
        val p = Placement(0.4f, 0.6f, 0.25f, 0f)
        listOf(1000 to 800, 4000 to 3200).forEach { (w, h) ->
            assertEquals(0.4f * w, p.centerX * w, delta)
            assertEquals(0.6f * h, p.centerY * h, delta)
            assertEquals(0.25f * w, p.sizeFraction * w, delta)
        }
    }

    @Test
    fun clampKeepsTheCenterInsideAndSizeInBounds() {
        val c = PlacementMath.clamp(Placement(-1f, 2f, 10f, 0f))
        assertEquals(0f, c.centerX, 0f)
        assertEquals(1f, c.centerY, 0f)
        assertEquals(PlacementMath.MAX_SIZE, c.sizeFraction, 0f)
        assertEquals(PlacementMath.MIN_SIZE, PlacementMath.clamp(Placement(0.5f, 0.5f, 0f, 0f)).sizeFraction, 0f)
    }

    @Test
    fun rotationIsNormalized() {
        assertEquals(-170f, PlacementMath.normalizeDegrees(190f), delta)
        assertEquals(180f, PlacementMath.normalizeDegrees(-180f), delta)
        assertEquals(10f, PlacementMath.normalizeDegrees(370f), delta)
    }

    @Test
    fun gestureMovesScalesAndRotates() {
        val g = PlacementMath.applyGesture(Placement(0.5f, 0.5f, 0.2f, 0f), 0.1f, -0.1f, 2f, 30f)
        assertEquals(0.6f, g.centerX, delta)
        assertEquals(0.4f, g.centerY, delta)
        assertEquals(0.4f, g.sizeFraction, delta)
        assertEquals(30f, g.rotationDeg, delta)
    }

    @Test
    fun heightFractionDependsOnTheAspectRatios() {
        // A 2:1 mark, 0.5 of the width of a 4:3 landscape image: 0.25 of the width tall = 0.25 * 4/3 of... see below.
        val h = PlacementMath.heightFraction(Placement(0.5f, 0.5f, 0.5f, 0f), imageAspect = 4f / 3f, markAspect = 2f)
        assertEquals(0.5f * (4f / 3f) / 2f, h, delta)
    }

    @Test
    fun halfExtentsOfAnUnrotatedAndAQuarterTurnedMark() {
        val flat = PlacementMath.halfExtents(Placement(0.5f, 0.5f, 0.4f, 0f), 1f, 2f)
        assertEquals(0.2f, flat.first, delta)
        assertEquals(0.1f, flat.second, delta)
        val turned = PlacementMath.halfExtents(Placement(0.5f, 0.5f, 0.4f, 90f), 1f, 2f)
        assertEquals(0.1f, turned.first, delta)
        assertEquals(0.2f, turned.second, delta)
    }

    @Test
    fun handleRoundTripsSizeAndRotation() {
        val p = Placement(0.5f, 0.5f, 0.3f, 25f)
        val (hx, hy) = PlacementMath.handlePosition(p, 1.5f, 2f)
        val (size, rot) = PlacementMath.handleToSizeAndRotation(hx - p.centerX, hy - p.centerY, 1.5f, 2f)
        assertEquals(0.3f, size, delta)
        assertEquals(25f, rot, 0.05f)
    }

    @Test
    fun snapsToTheImageCenter() {
        val r = Snapper.snap(Placement(0.505f, 0.49f, 0.2f, 0f), 1f, 2f, 0.03f, enabled = true)
        assertEquals(0.5f, r.placement.centerX, delta)
        assertEquals(0.5f, r.placement.centerY, delta)
        assertEquals(0.5f, r.guideX)
        assertEquals(0.5f, r.guideY)
    }

    @Test
    fun snapsTheMarkEdgeToTheMargin() {
        // Mark 0.2 wide: left edge at the 0.03 margin means a center at 0.13.
        val r = Snapper.snap(Placement(0.14f, 0.3f, 0.2f, 0f), 1f, 2f, 0.03f, enabled = true)
        assertEquals(0.13f, r.placement.centerX, delta)
        assertEquals(0.03f, r.guideX)
        assertNull(r.guideY)
    }

    @Test
    fun snapsTheMarkFlushToAnEdge() {
        val r = Snapper.snap(Placement(0.105f, 0.3f, 0.2f, 0f), 1f, 2f, 0.03f, enabled = true)
        assertEquals(0.1f, r.placement.centerX, delta)
        assertEquals(0f, r.guideX)
    }

    @Test
    fun snapsRotationToDetents() {
        val r = Snapper.snap(Placement(0.3f, 0.3f, 0.2f, 44f), 1f, 2f, 0.03f, enabled = true)
        assertEquals(45f, r.placement.rotationDeg, delta)
        assertTrue(r.rotationSnapped)
        assertFalse(Snapper.snap(Placement(0.3f, 0.3f, 0.2f, 30f), 1f, 2f, 0.03f, enabled = true).rotationSnapped)
    }

    @Test
    fun disabledSnappingChangesNothing() {
        val p = Placement(0.505f, 0.49f, 0.2f, 44f)
        val r = Snapper.snap(p, 1f, 2f, 0.03f, enabled = false)
        assertEquals(p, r.placement)
        assertFalse(r.isSnapping)
    }

    @Test
    fun farFromEveryTargetNothingSnaps() {
        val r = Snapper.snap(Placement(0.3f, 0.7f, 0.1f, 12f), 1f, 2f, 0.03f, enabled = true)
        assertNotNull(r)
        assertFalse(r.isSnapping)
    }
}
