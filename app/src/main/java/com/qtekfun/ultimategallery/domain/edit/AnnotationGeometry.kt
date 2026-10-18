package com.qtekfun.ultimategallery.domain.edit

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pure geometry of the drawn items: hit testing, moving and the shape of arrow heads. Distances are in image pixels. */
object AnnotationGeometry {
    private const val ARROW_HEAD_ANGLE = 0.5
    private const val ARROW_HEAD_PER_WIDTH = 4.5f
    private const val ARROW_HEAD_MIN_PX = 10f
    private const val MIN_TEXT_SIZE = 0.015f
    private const val MAX_TEXT_SIZE = 0.6f

    /** Half the width and height of a text note, in image pixels, before rotation. */
    fun interface TextExtent {
        fun halfSize(text: Annotation.Text, width: Int, height: Int): Pair<Float, Float>
    }

    /**
     * The id of the topmost item under [point] (fractions of a [width] x [height] image), or null. [slop] widens thin items so
     * they can be picked with a finger.
     */
    fun hitTest(annotations: List<Annotation>, point: EditPoint, width: Int, height: Int, slop: Float, extent: TextExtent): Long? {
        val px = point.x * width
        val py = point.y * height
        val short = min(width, height).toFloat()
        return annotations.lastOrNull { a ->
            when (a) {
                is Annotation.Stroke -> polylineDistance(a.points, px, py, width, height) <= a.width * short / 2f + slop
                is Annotation.Shape -> hitsShape(a, px, py, width, height, a.width * short / 2f + slop)
                is Annotation.Text -> hitsText(a, px, py, width, height, slop, extent)
            }
        }?.id
    }

    /** [annotation] moved by [dx], [dy] (fractions of the image). */
    fun move(annotation: Annotation, dx: Float, dy: Float): Annotation {
        fun EditPoint.moved() = EditPoint(x + dx, y + dy)
        return when (annotation) {
            is Annotation.Stroke -> annotation.copy(points = annotation.points.map { it.moved() })
            is Annotation.Shape -> annotation.copy(start = annotation.start.moved(), end = annotation.end.moved())
            is Annotation.Text -> annotation.copy(center = annotation.center.moved())
        }
    }

    /** A text note scaled by [zoom] (size kept within bounds) and turned by [rotationDeg] more. */
    fun transformText(text: Annotation.Text, zoom: Float, rotationDeg: Float): Annotation.Text =
        text.copy(size = (text.size * zoom).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE), rotationDeg = normalizeDegrees(text.rotationDeg + rotationDeg))

    /** The two back corners of the head of an arrow from ([x0], [y0]) to ([x1], [y1]) drawn [strokeWidth] pixels wide. */
    fun arrowHead(x0: Float, y0: Float, x1: Float, y1: Float, strokeWidth: Float): List<Pair<Float, Float>> {
        val length = max(strokeWidth * ARROW_HEAD_PER_WIDTH, ARROW_HEAD_MIN_PX).coerceAtMost(hypot(x1 - x0, y1 - y0) * 0.6f)
        val angle = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
        return listOf(angle + Math.PI - ARROW_HEAD_ANGLE, angle + Math.PI + ARROW_HEAD_ANGLE).map { a ->
            (x1 + length * cos(a).toFloat()) to (y1 + length * sin(a).toFloat())
        }
    }

    /** Where an arrow's line stops so that its square end hides inside the head. */
    fun arrowLineEnd(x0: Float, y0: Float, x1: Float, y1: Float, strokeWidth: Float): Pair<Float, Float> {
        val length = hypot(x1 - x0, y1 - y0)
        if (length == 0f) return x1 to y1
        val back = min(strokeWidth, length / 2f)
        return (x1 - (x1 - x0) / length * back) to (y1 - (y1 - y0) / length * back)
    }

    /** The shortest distance from ([px], [py]) to the polyline through [points], in pixels of a [width] x [height] image. */
    fun polylineDistance(points: List<EditPoint>, px: Float, py: Float, width: Int, height: Int): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) return hypot(points[0].x * width - px, points[0].y * height - py)
        var best = Float.MAX_VALUE
        for (i in 1 until points.size) {
            val d = segmentDistance(points[i - 1].x * width, points[i - 1].y * height, points[i].x * width, points[i].y * height, px, py)
            if (d < best) best = d
        }
        return best
    }

    fun segmentDistance(x0: Float, y0: Float, x1: Float, y1: Float, px: Float, py: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0f) 0f else (((px - x0) * dx + (py - y0) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(x0 + t * dx - px, y0 + t * dy - py)
    }

    private fun hitsShape(shape: Annotation.Shape, px: Float, py: Float, width: Int, height: Int, reach: Float): Boolean {
        val x0 = shape.start.x * width
        val y0 = shape.start.y * height
        val x1 = shape.end.x * width
        val y1 = shape.end.y * height
        return when (shape.kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> segmentDistance(x0, y0, x1, y1, px, py) <= reach
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> {
                val inside = px in min(x0, x1) - reach..max(x0, x1) + reach && py in min(y0, y1) - reach..max(y0, y1) + reach
                if (!inside) {
                    false
                } else if (shape.filled) {
                    true
                } else {
                    // An outline is hit near its border, not in its middle, so items behind it stay reachable.
                    val innerW = abs(x1 - x0) / 2f - reach
                    val innerH = abs(y1 - y0) / 2f - reach
                    innerW <= 0f || innerH <= 0f || abs(px - (x0 + x1) / 2f) > innerW || abs(py - (y0 + y1) / 2f) > innerH
                }
            }
        }
    }

    private fun hitsText(text: Annotation.Text, px: Float, py: Float, width: Int, height: Int, slop: Float, extent: TextExtent): Boolean {
        val (hw, hh) = extent.halfSize(text, width, height)
        // Turn the point into the text's own frame.
        val angle = Math.toRadians(-text.rotationDeg.toDouble())
        val dx = px - text.center.x * width
        val dy = py - text.center.y * height
        val lx = dx * cos(angle).toFloat() - dy * sin(angle).toFloat()
        val ly = dx * sin(angle).toFloat() + dy * cos(angle).toFloat()
        return abs(lx) <= hw + slop && abs(ly) <= hh + slop
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}
