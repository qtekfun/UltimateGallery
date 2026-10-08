package com.qtekfun.ultimategallery.feature.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStateTest {
    private fun state() = ZoomState().apply {
        viewSize = IntSize(1000, 2000)
        contentSize = Size(1000f, 750f)
    }

    @Test
    fun atScaleOneThereIsNothingToPan() {
        val s = state()
        assertEquals(Offset.Zero, s.maxOffset())
        assertEquals(Offset.Zero, s.panBy(Offset(50f, 50f)))
        assertFalse(s.isZoomed)
    }

    @Test
    fun zoomingWidensTheBoundsByTheContentSize() {
        val s = state()
        s.applyGesture(Offset.Zero, Offset.Zero, 2f)
        assertEquals(2f, s.scale, 0.001f)
        // Content 2000 wide in a 1000 wide view: 500 either way. Content 1500 tall in a 2000 view: none.
        assertEquals(Offset(500f, 0f), s.maxOffset())
        assertTrue(s.isZoomed)
    }

    @Test
    fun panIsClampedToTheBoundsAndReportsWhatWasApplied() {
        val s = state()
        s.applyGesture(Offset.Zero, Offset.Zero, 2f)
        val applied = s.panBy(Offset(900f, 40f))
        assertEquals(Offset(500f, 0f), applied)
        assertEquals(Offset(500f, 0f), s.offset)
        assertEquals(Offset.Zero, s.panBy(Offset(10f, 0f)))
    }

    @Test
    fun zoomKeepsThePointUnderTheFingersFixed() {
        val s = state()
        val finger = Offset(200f, 0f)
        s.applyGesture(finger, Offset.Zero, 2f)
        // The content point that was under the finger must still be under it: offset = -finger * (scale - 1).
        assertEquals(-200f, s.offset.x, 0.01f)
    }

    @Test
    fun scaleIsLimitedWithALittleOvershoot() {
        val s = state()
        s.applyGesture(Offset.Zero, Offset.Zero, 100f)
        assertTrue(s.scale <= ZoomState.DEFAULT_MAX_SCALE * 1.2f)
        assertTrue(s.scale > ZoomState.DEFAULT_MAX_SCALE)
    }

    @Test
    fun doubleTapZoomFillsTheViewForTallScreens() {
        val s = state()
        // Fit content is 1000x750 in a 1000x2000 view: filling the view needs 2.67x, more than 2.5x.
        assertEquals(2000f / 750f, s.doubleTapScale(), 0.01f)
    }

    @Test
    fun resetReturnsToTheFittedImage() {
        val s = state()
        s.applyGesture(Offset.Zero, Offset(10f, 0f), 3f)
        s.reset()
        assertEquals(1f, s.scale, 0f)
        assertEquals(Offset.Zero, s.offset)
    }
}
