package com.qtekfun.ultimategallery.domain.edit

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationGeometryTest {
    private val extent = AnnotationGeometry.TextExtent { _, _, _ -> 20f to 5f }
    private val red = 0xFFFF0000.toInt()

    private fun hit(annotations: List<Annotation>, x: Float, y: Float) =
        AnnotationGeometry.hitTest(annotations, EditPoint(x / 100f, y / 100f), 100, 100, slop = 2f, extent = extent)

    @Test
    fun strokesAreHitNearTheirLine() {
        val stroke = Annotation.Stroke(1, listOf(EditPoint(0.1f, 0.5f), EditPoint(0.9f, 0.5f)), red, 0.04f, StrokeStyle.PEN)
        assertEquals(1L, hit(listOf(stroke), 50f, 53f))
        assertNull(hit(listOf(stroke), 50f, 60f))
    }

    @Test
    fun outlinesAreHitOnTheBorderAndFilledShapesInside() {
        val outline = Annotation.Shape(1, ShapeKind.RECTANGLE, EditPoint(0.2f, 0.2f), EditPoint(0.8f, 0.8f), red, 0.02f, filled = false)
        assertEquals(1L, hit(listOf(outline), 20f, 50f))
        assertNull(hit(listOf(outline), 50f, 50f))
        assertEquals(1L, hit(listOf(outline.copy(filled = true)), 50f, 50f))
    }

    @Test
    fun rotatedTextIsHitInItsOwnFrame() {
        val text = Annotation.Text(1, "Hi", EditPoint(0.5f, 0.5f), 0.1f, 90f, red, FONT, boxed = false)
        // Turned a quarter, the 40 x 10 box stands upright.
        assertEquals(1L, hit(listOf(text), 50f, 68f))
        assertNull(hit(listOf(text), 68f, 50f))
    }

    @Test
    fun theTopmostItemWins() {
        val below = Annotation.Shape(1, ShapeKind.RECTANGLE, EditPoint(0f, 0f), EditPoint(1f, 1f), red, 0.02f, filled = true)
        val above = Annotation.Shape(2, ShapeKind.ELLIPSE, EditPoint(0.4f, 0.4f), EditPoint(0.6f, 0.6f), red, 0.02f, filled = true)
        assertEquals(2L, hit(listOf(below, above), 50f, 50f))
        assertEquals(1L, hit(listOf(below, above), 10f, 10f))
    }

    @Test
    fun moveShiftsEveryPoint() {
        val shape = Annotation.Shape(1, ShapeKind.LINE, EditPoint(0.1f, 0.1f), EditPoint(0.2f, 0.3f), red, 0.02f, filled = false)
        val moved = AnnotationGeometry.move(shape, 0.1f, -0.05f) as Annotation.Shape
        assertEquals(0.2f, moved.start.x, 1e-6f)
        assertEquals(0.25f, moved.end.y, 1e-6f)
    }

    @Test
    fun textTransformClampsSizeAndWrapsAngle() {
        val text = Annotation.Text(1, "Hi", EditPoint(0.5f, 0.5f), 0.1f, 170f, red, FONT, boxed = false)
        val t = AnnotationGeometry.transformText(text, zoom = 100f, rotationDeg = 20f)
        assertEquals(0.6f, t.size, 1e-6f)
        assertEquals(-170f, t.rotationDeg, 1e-4f)
    }

    @Test
    fun arrowHeadIsSymmetricBehindTheTip() {
        val head = AnnotationGeometry.arrowHead(0f, 50f, 100f, 50f, 4f)
        assertEquals(head[0].first, head[1].first, 1e-3f)
        assertEquals(50f - head[0].second, head[1].second - 50f, 1e-3f)
        assertEquals(true, head[0].first < 100f)
        val (lx, _) = AnnotationGeometry.arrowLineEnd(0f, 50f, 100f, 50f, 4f)
        assertEquals(96f, lx, 1e-3f)
        assertEquals(4f, hypot(100f - lx, 0f), 1e-3f)
    }

    private companion object {
        const val FONT = "default"
    }
}
