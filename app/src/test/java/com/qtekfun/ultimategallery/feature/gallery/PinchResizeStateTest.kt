package com.qtekfun.ultimategallery.feature.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinchResizeStateTest {
    @Test
    fun smallPinchesOnlyScaleVisually() {
        val s = PinchResizeState(2, 8)
        assertEquals(4, s.onZoom(4, 1.05f))
        assertEquals(1.05f, s.scale, 0.001f)
    }

    @Test
    fun zoomingInReducesTheColumnsAndRebasesTheScale() {
        val s = PinchResizeState(2, 8)
        var columns = 4
        columns = s.onZoom(columns, 1.5f)
        assertEquals(3, columns)
        assertTrue("scale ${s.scale}", s.scale in 1.0f..1.2f)
    }

    @Test
    fun zoomingOutAddsColumns() {
        val s = PinchResizeState(2, 8)
        var columns = 4
        columns = s.onZoom(columns, 0.6f)
        assertEquals(5, columns)
    }

    @Test
    fun columnsStayWithinTheBounds() {
        val s = PinchResizeState(2, 8)
        var columns = 3
        repeat(10) { columns = s.onZoom(columns, 1.4f) }
        assertEquals(2, columns)
        columns = 7
        repeat(10) { columns = s.onZoom(columns, 0.7f) }
        assertEquals(8, columns)
    }

    @Test
    fun aSteadyPinchPassesThroughSeveralCounts() {
        val s = PinchResizeState(1, 4)
        var columns = 4
        val seen = mutableSetOf(columns)
        repeat(40) {
            columns = s.onZoom(columns, 1.05f)
            seen += columns
        }
        assertEquals(setOf(4, 3, 2, 1), seen)
    }
}
