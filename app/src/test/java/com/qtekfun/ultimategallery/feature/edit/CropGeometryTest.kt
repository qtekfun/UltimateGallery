package com.qtekfun.ultimategallery.feature.edit

import com.qtekfun.ultimategallery.data.edit.CropRect
import com.qtekfun.ultimategallery.data.edit.CropRotateSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {
    private val canvas = CanvasSize(400f, 300f)

    private fun pixelAspect(rect: CropRect, size: CanvasSize = canvas) = rect.width * size.width / (rect.height * size.height)

    private fun assertRect(expected: CropRect, actual: CropRect, delta: Float = 1e-4f) {
        assertEquals(expected.left, actual.left, delta)
        assertEquals(expected.top, actual.top, delta)
        assertEquals(expected.right, actual.right, delta)
        assertEquals(expected.bottom, actual.bottom, delta)
    }

    @Test
    fun applyAspectGivesTheRequestedShapeInsideTheCurrentFrame() {
        for (aspect in listOf(1f, 4f / 3f, 3f / 4f, 16f / 9f)) {
            val rect = CropGeometry.applyAspect(CropRect.FULL, aspect, canvas)
            assertEquals(aspect, pixelAspect(rect), 1e-3f)
            assertTrue(rect.left >= 0f && rect.top >= 0f && rect.right <= 1f && rect.bottom <= 1f)
            assertEquals(0.5f, rect.centerX, 1e-5f)
            assertEquals(0.5f, rect.centerY, 1e-5f)
        }
    }

    @Test
    fun lockedCornerDragKeepsTheAspectAndTheAnchor() {
        val start = CropGeometry.applyAspect(CropRect(0.1f, 0.1f, 0.9f, 0.9f), 1f, canvas)
        val dragged = CropGeometry.drag(start, CropHandle.BOTTOM_RIGHT, -0.1f, -0.05f, 1f, canvas, 0f)
        assertEquals(1f, pixelAspect(dragged), 1e-3f)
        assertEquals(start.left, dragged.left, 1e-5f)
        assertEquals(start.top, dragged.top, 1e-5f)
        assertTrue(dragged.width < start.width)
    }

    @Test
    fun lockedEdgeDragKeepsTheAspect() {
        val aspect = 16f / 9f
        val start = CropGeometry.applyAspect(CropRect(0.2f, 0.2f, 0.8f, 0.8f), aspect, canvas)
        for (handle in listOf(CropHandle.LEFT, CropHandle.RIGHT, CropHandle.TOP, CropHandle.BOTTOM)) {
            val dragged = CropGeometry.drag(start, handle, 0.03f, -0.03f, aspect, canvas, 0f)
            assertEquals("$handle", aspect, pixelAspect(dragged), 1e-2f)
        }
    }

    @Test
    fun freeDragNeverLeavesTheImage() {
        val start = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        val grown = CropGeometry.drag(start, CropHandle.BOTTOM_RIGHT, 5f, 5f, null, canvas, 0f)
        assertEquals(1f, grown.right, 1e-3f)
        assertEquals(1f, grown.bottom, 1e-3f)
        val moved = CropGeometry.drag(start, CropHandle.MOVE, -5f, -5f, null, canvas, 0f)
        assertEquals(0f, moved.left, 1e-3f)
        assertEquals(0f, moved.top, 1e-3f)
        assertEquals(start.width, moved.width, 1e-3f)
        assertEquals(start.height, moved.height, 1e-3f)
    }

    @Test
    fun freeDragSlidesAlongABorder() {
        val start = CropRect(0f, 0.2f, 0.4f, 0.6f)
        val moved = CropGeometry.drag(start, CropHandle.MOVE, -0.2f, 0.1f, null, canvas, 0f)
        assertEquals(0f, moved.left, 1e-3f)
        assertEquals(0.3f, moved.top, 1e-3f)
    }

    @Test
    fun theFrameKeepsAMinimumSize() {
        val start = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        val shrunk = CropGeometry.drag(start, CropHandle.BOTTOM_RIGHT, -5f, -5f, null, canvas, 0f)
        assertEquals(CropGeometry.MIN_FRACTION, shrunk.width, 1e-4f)
        assertEquals(CropGeometry.MIN_FRACTION, shrunk.height, 1e-4f)
        val locked = CropGeometry.drag(start, CropHandle.BOTTOM_RIGHT, -5f, -5f, 1f, canvas, 0f)
        assertTrue(locked.width >= CropGeometry.MIN_FRACTION - 1e-4f)
        assertEquals(1f, pixelAspect(locked), 1e-3f)
    }

    @Test
    fun straightenedDragStaysInsideTheRotatedImage() {
        val angle = 20f
        val start = CropGeometry.largestInscribed(canvas, angle)
        for (handle in CropHandle.entries) {
            val result = CropGeometry.drag(start, handle, 0.4f, 0.4f, null, canvas, angle)
            assertTrue("$handle", CropGeometry.fits(result, canvas, angle))
            val back = CropGeometry.drag(result, handle, -0.7f, -0.7f, null, canvas, angle)
            assertTrue("$handle", CropGeometry.fits(back, canvas, angle))
        }
    }

    @Test
    fun largestInscribedAtZeroIsTheWholeImage() {
        assertRect(CropRect.FULL, CropGeometry.largestInscribed(canvas, 0f))
    }

    @Test
    fun largestInscribedOfASquareAtFortyFiveDegrees() {
        val square = CanvasSize(100f, 100f)
        val rect = CropGeometry.largestInscribed(square, 45f)
        assertEquals(0.7071f, rect.width, 1e-3f)
        assertEquals(0.7071f, rect.height, 1e-3f)
        assertTrue(CropGeometry.fits(rect, square, 45f))
        assertFalse(CropGeometry.fits(CropRect(0.1f, 0.1f, 0.9f, 0.9f), square, 45f))
    }

    @Test
    fun largestInscribedFitsAndIsTight() {
        for (angle in listOf(-45f, -30f, -7.5f, 3f, 12f, 29f, 45f)) {
            for (aspect in listOf(null, 1f, 16f / 9f, 3f / 4f)) {
                val rect = CropGeometry.largestInscribed(canvas, angle, aspect)
                assertTrue("$angle $aspect", CropGeometry.fits(rect, canvas, angle))
                assertEquals(aspect ?: (canvas.width / canvas.height), pixelAspect(rect), 1e-3f)
                val bigger = CropRect(
                    0.5f - rect.width / 2f * 1.02f,
                    0.5f - rect.height / 2f * 1.02f,
                    0.5f + rect.width / 2f * 1.02f,
                    0.5f + rect.height / 2f * 1.02f
                )
                assertFalse("$angle $aspect", CropGeometry.fits(bigger, canvas, angle))
            }
        }
    }

    @Test
    fun constrainShrinksAFrameThatWouldShowEmptyCorners() {
        val constrained = CropGeometry.constrain(CropRect.FULL, canvas, 15f)
        assertTrue(CropGeometry.fits(constrained, canvas, 15f))
        assertTrue(constrained.width < 1f)
        assertEquals(pixelAspect(CropRect.FULL), pixelAspect(constrained), 1e-3f)
        val small = CropRect(0.45f, 0.45f, 0.55f, 0.55f)
        assertRect(small, CropGeometry.constrain(small, canvas, 15f))
    }

    @Test
    fun rotatingARectMovesItWithTheImage() {
        val rect = CropRect(0.1f, 0.2f, 0.4f, 0.5f)
        // The top left of the canvas goes to the top right when turning clockwise.
        assertRect(CropRect(0.5f, 0.1f, 0.8f, 0.4f), CropGeometry.rotateRect(rect, clockwise = true))
        assertRect(CropRect(0.2f, 0.6f, 0.5f, 0.9f), CropGeometry.rotateRect(rect, clockwise = false))
        assertRect(rect, CropGeometry.rotateRect(CropGeometry.rotateRect(rect, true), false))
        var turned = rect
        repeat(4) { turned = CropGeometry.rotateRect(turned, true) }
        assertRect(rect, turned)
    }

    @Test
    fun flippingARectMirrorsIt() {
        val rect = CropRect(0.1f, 0.2f, 0.4f, 0.5f)
        assertRect(CropRect(0.6f, 0.2f, 0.9f, 0.5f), CropGeometry.flipRect(rect, horizontal = true))
        assertRect(CropRect(0.1f, 0.5f, 0.4f, 0.8f), CropGeometry.flipRect(rect, horizontal = false))
        assertRect(rect, CropGeometry.flipRect(CropGeometry.flipRect(rect, true), true))
    }

    @Test
    fun canvasSwapsSidesOnOddTurns() {
        assertEquals(CanvasSize(300f, 400f), CropGeometry.canvasSize(400f, 300f, 1))
        assertEquals(CanvasSize(400f, 300f), CropGeometry.canvasSize(400f, 300f, 2))
        assertEquals(CanvasSize(300f, 400f), CropGeometry.canvasSize(400f, 300f, 3))
    }

    @Test
    fun hitTestPrefersCornersThenEdgesThenTheInside() {
        val rect = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val slop = 20f
        assertEquals(CropHandle.TOP_LEFT, CropGeometry.hitTest(rect, 100f, 75f, canvas, slop))
        assertEquals(CropHandle.BOTTOM_RIGHT, CropGeometry.hitTest(rect, 305f, 220f, canvas, slop))
        assertEquals(CropHandle.TOP, CropGeometry.hitTest(rect, 200f, 80f, canvas, slop))
        assertEquals(CropHandle.LEFT, CropGeometry.hitTest(rect, 95f, 150f, canvas, slop))
        assertEquals(CropHandle.MOVE, CropGeometry.hitTest(rect, 200f, 150f, canvas, slop))
        assertNull(CropGeometry.hitTest(rect, 5f, 5f, canvas, slop))
    }

    @Test
    fun snappedAspectRecognisesThePresets() {
        assertEquals(AspectRatio.SQUARE, CropGeometry.snappedAspect(CropGeometry.applyAspect(CropRect.FULL, 1f, canvas), canvas))
        assertEquals(AspectRatio.R16_9, CropGeometry.snappedAspect(CropGeometry.applyAspect(CropRect.FULL, 16f / 9f, canvas), canvas))
        assertNull(CropGeometry.snappedAspect(CropRect(0f, 0f, 0.5f, 0.6f), canvas))
        assertNotNull(AspectRatio.entries.firstOrNull { it.ratio == null })
        assertEquals(AspectRatio.R3_4, AspectRatio.R4_3.turned())
        assertEquals(AspectRatio.R16_9, AspectRatio.R9_16.turned())
    }

    @Test
    fun outputSizeFollowsTurnsAndCrop() {
        assertEquals(400 to 300, CropGeometry.outputSize(400, 300, CropRotateSpec()))
        assertEquals(300 to 400, CropGeometry.outputSize(400, 300, CropRotateSpec(quarterTurns = 1)))
        val cropped = CropRotateSpec(quarterTurns = 1, crop = CropRect(0f, 0f, 0.5f, 0.25f))
        assertEquals(150 to 100, CropGeometry.outputSize(400, 300, cropped))
    }

    private fun map(spec: CropRotateSpec, x: Double, y: Double, width: Int = 400, height: Int = 300): Pair<Double, Double> {
        val m = CropGeometry.compositeAffine(width, height, spec)
        return (m[0] * x + m[2] * y + m[4]) to (m[1] * x + m[3] * y + m[5])
    }

    private fun assertPoint(expected: Pair<Double, Double>, actual: Pair<Double, Double>) {
        assertEquals(expected.first, actual.first, 1e-6)
        assertEquals(expected.second, actual.second, 1e-6)
    }

    @Test
    fun compositeTransformMapsCornersLikeTheScreenDoes() {
        assertPoint(0.0 to 0.0, map(CropRotateSpec(), 0.0, 0.0))
        // Clockwise turn: the top left corner lands at the top right of a 300 x 400 canvas.
        assertPoint(300.0 to 0.0, map(CropRotateSpec(quarterTurns = 1), 0.0, 0.0))
        assertPoint(0.0 to 400.0, map(CropRotateSpec(quarterTurns = 1), 400.0, 300.0))
        assertPoint(0.0 to 0.0, map(CropRotateSpec(quarterTurns = 1), 0.0, 300.0))
        assertPoint(0.0 to 300.0, map(CropRotateSpec(quarterTurns = 2), 400.0, 0.0))
        assertPoint(0.0 to 0.0, map(CropRotateSpec(quarterTurns = 3), 400.0, 0.0))
        assertPoint(400.0 to 0.0, map(CropRotateSpec(flipH = true), 0.0, 0.0))
        assertPoint(0.0 to 300.0, map(CropRotateSpec(flipV = true), 0.0, 0.0))
    }

    @Test
    fun compositeTransformAppliesFlipAfterTheTurn() {
        // Turn clockwise, then mirror horizontally: the top left corner goes top right, then top left.
        assertPoint(0.0 to 0.0, map(CropRotateSpec(quarterTurns = 1, flipH = true), 0.0, 0.0))
    }

    @Test
    fun compositeTransformShiftsByTheCropOrigin() {
        val spec = CropRotateSpec(crop = CropRect(0.25f, 0.5f, 1f, 1f))
        assertPoint(0.0 to 0.0, map(spec, 100.0, 150.0))
        assertPoint(300.0 to 150.0, map(spec, 400.0, 300.0))
    }

    @Test
    fun compositeTransformStraightensAroundTheCenter() {
        val spec = CropRotateSpec(straightenDeg = 90f)
        // The center stays put; a quarter-turn straighten moves the right edge midpoint to the bottom.
        assertPoint(200.0 to 150.0, map(spec, 200.0, 150.0))
        assertPoint(200.0 to 350.0, map(spec, 400.0, 150.0))
        val tilted = map(CropRotateSpec(straightenDeg = 10f), 400.0, 150.0)
        assertTrue(tilted.second > 150.0)
    }
}
