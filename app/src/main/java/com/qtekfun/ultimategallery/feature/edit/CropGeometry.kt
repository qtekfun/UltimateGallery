package com.qtekfun.ultimategallery.feature.edit

import com.qtekfun.ultimategallery.data.edit.CropRect
import com.qtekfun.ultimategallery.data.edit.CropRotateSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Aspect ratio lock of the crop frame, as width over height. */
enum class AspectRatio(val ratio: Float?) {
    FREE(null),
    SQUARE(1f),
    R4_3(4f / 3f),
    R3_4(3f / 4f),
    R16_9(16f / 9f),
    R9_16(9f / 16f);

    /** The same lock after the canvas turned by 90 degrees. */
    fun turned(): AspectRatio = when (this) {
        FREE -> FREE
        SQUARE -> SQUARE
        R4_3 -> R3_4
        R3_4 -> R4_3
        R16_9 -> R9_16
        R9_16 -> R16_9
    }
}

/** Size of the canvas the crop is drawn on, in any consistent unit (pixels or dp). */
data class CanvasSize(val width: Float, val height: Float)

/** Parts of the crop frame a finger can grab. */
enum class CropHandle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE }

/**
 * Pure math of the crop and rotate screen. All crop rectangles are fractions of the canvas, the photo
 * after the quarter turns and the flip. The straightened photo is the canvas rotated by an angle
 * around its center, so a crop is valid only while its four corners stay inside that rotated
 * rectangle; this is what keeps empty corners out of the result.
 */
@Suppress("TooManyFunctions")
object CropGeometry {
    const val MAX_STRAIGHTEN = 45f

    /** The smallest side of a crop, as a fraction of the canvas. */
    const val MIN_FRACTION = 0.1f

    private const val EPSILON = 1e-4f
    private const val SEARCH_STEPS = 28
    private const val SNAP_TOLERANCE = 0.015f
    private const val HIT_SLOP_DIVISOR = 3f
    private const val QUARTER = 90.0
    private const val TURNS = 4

    private val LEFT_HANDLES = setOf(CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT)
    private val RIGHT_HANDLES = setOf(CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT)
    private val TOP_HANDLES = setOf(CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT)
    private val BOTTOM_HANDLES = setOf(CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT)

    /** The canvas of a [width] x [height] photo after [quarterTurns] turns. */
    fun canvasSize(width: Float, height: Float, quarterTurns: Int): CanvasSize =
        if (quarterTurns % 2 != 0) CanvasSize(height, width) else CanvasSize(width, height)

    /** True when [rect] lies entirely inside the photo straightened by [straightenDeg]. */
    fun fits(rect: CropRect, canvas: CanvasSize, straightenDeg: Float): Boolean {
        val radians = Math.toRadians(-straightenDeg.toDouble())
        val c = cos(radians)
        val s = sin(radians)
        val halfW = canvas.width / 2.0
        val halfH = canvas.height / 2.0
        val slack = EPSILON * max(canvas.width, canvas.height)
        for (x in floatArrayOf(rect.left, rect.right)) {
            for (y in floatArrayOf(rect.top, rect.bottom)) {
                val px = (x - 0.5) * canvas.width
                val py = (y - 0.5) * canvas.height
                val rx = px * c - py * s
                val ry = px * s + py * c
                if (abs(rx) > halfW + slack || abs(ry) > halfH + slack) return false
            }
        }
        return true
    }

    /**
     * The largest centered crop of the given pixel [aspect] (width over height; the canvas aspect when
     * null) that fits inside the photo straightened by [straightenDeg].
     */
    fun largestInscribed(canvas: CanvasSize, straightenDeg: Float, aspect: Float? = null): CropRect {
        val a = aspect ?: (canvas.width / canvas.height)
        val radians = Math.toRadians(straightenDeg.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        val w = min(canvas.width / (c + s / a), canvas.height / (s + c / a))
        val h = w / a
        return centered(w / canvas.width, h / canvas.height)
    }

    /**
     * Shrinks [rect] towards the center of the canvas, keeping its shape, until it fits inside the
     * photo straightened by [straightenDeg]. A rectangle that already fits is returned as it is.
     */
    fun constrain(rect: CropRect, canvas: CanvasSize, straightenDeg: Float): CropRect {
        if (fits(rect, canvas, straightenDeg)) return rect
        var lo = 0f
        var hi = 1f
        repeat(SEARCH_STEPS) {
            val mid = (lo + hi) / 2f
            if (fits(homothety(rect, mid), canvas, straightenDeg)) lo = mid else hi = mid
        }
        return homothety(rect, lo)
    }

    /**
     * Moves or resizes [rect] by dragging [handle] by ([dx], [dy]), both fractions of the canvas. With a
     * pixel [aspect] lock the frame keeps that shape. The result never leaves the straightened photo and
     * slides along its border instead of stopping on contact.
     */
    fun drag(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, aspect: Float?, canvas: CanvasSize, straightenDeg: Float): CropRect {
        val start = constrain(rect, canvas, straightenDeg)
        if (aspect != null && handle != CropHandle.MOVE) {
            return approach(start, lockedCandidate(start, handle, dx, dy, aspect, canvas), canvas, straightenDeg)
        }
        val afterX = approach(start, freeCandidate(start, handle, dx, 0f), canvas, straightenDeg)
        return approach(afterX, freeCandidate(afterX, handle, 0f, dy), canvas, straightenDeg)
    }

    /** The biggest frame of the pixel [aspect] that fits inside [rect], with the same center. */
    fun applyAspect(rect: CropRect, aspect: Float, canvas: CanvasSize): CropRect {
        val currentW = rect.width * canvas.width
        val currentH = rect.height * canvas.height
        val w: Float
        val h: Float
        if (currentW / currentH > aspect) {
            h = currentH
            w = h * aspect
        } else {
            w = currentW
            h = w / aspect
        }
        val nw = w / canvas.width
        val nh = h / canvas.height
        return CropRect(rect.centerX - nw / 2f, rect.centerY - nh / 2f, rect.centerX + nw / 2f, rect.centerY + nh / 2f)
    }

    /** [rect] after the canvas turned by 90 degrees. */
    fun rotateRect(rect: CropRect, clockwise: Boolean): CropRect = if (clockwise) {
        CropRect(1f - rect.bottom, rect.left, 1f - rect.top, rect.right)
    } else {
        CropRect(rect.top, 1f - rect.right, rect.bottom, 1f - rect.left)
    }

    /** [rect] after the canvas was mirrored. */
    fun flipRect(rect: CropRect, horizontal: Boolean): CropRect = if (horizontal) {
        CropRect(1f - rect.right, rect.top, 1f - rect.left, rect.bottom)
    } else {
        CropRect(rect.left, 1f - rect.bottom, rect.right, 1f - rect.top)
    }

    /**
     * Which part of the frame is under ([x], [y]), with the frame and the point in the same unit as
     * [canvas]. Corners win over edges and edges over the inside; null when the point is elsewhere.
     */
    @Suppress("CyclomaticComplexMethod")
    fun hitTest(rect: CropRect, x: Float, y: Float, canvas: CanvasSize, slop: Float): CropHandle? {
        val l = rect.left * canvas.width
        val r = rect.right * canvas.width
        val t = rect.top * canvas.height
        val b = rect.bottom * canvas.height
        val reach = min(slop, min(r - l, b - t) / HIT_SLOP_DIVISOR)
        val nearLeft = abs(x - l) <= reach
        val nearRight = abs(x - r) <= reach
        val nearTop = abs(y - t) <= reach
        val nearBottom = abs(y - b) <= reach
        val withinX = x in (l - reach)..(r + reach)
        val withinY = y in (t - reach)..(b + reach)
        return when {
            nearLeft && nearTop -> CropHandle.TOP_LEFT
            nearRight && nearTop -> CropHandle.TOP_RIGHT
            nearLeft && nearBottom -> CropHandle.BOTTOM_LEFT
            nearRight && nearBottom -> CropHandle.BOTTOM_RIGHT
            nearTop && withinX -> CropHandle.TOP
            nearBottom && withinX -> CropHandle.BOTTOM
            nearLeft && withinY -> CropHandle.LEFT
            nearRight && withinY -> CropHandle.RIGHT
            x in l..r && y in t..b -> CropHandle.MOVE
            else -> null
        }
    }

    /** The preset whose shape [rect] currently has (within 1.5 percent), or null. */
    fun snappedAspect(rect: CropRect, canvas: CanvasSize): AspectRatio? {
        val shape = rect.width * canvas.width / (rect.height * canvas.height)
        return AspectRatio.entries.firstOrNull { preset ->
            preset.ratio?.let { abs(shape / it - 1f) <= SNAP_TOLERANCE } == true
        }
    }

    /** Size in pixels of the photo produced by [spec] from a [srcWidth] x [srcHeight] source. */
    fun outputSize(srcWidth: Int, srcHeight: Int, spec: CropRotateSpec): Pair<Int, Int> {
        val canvas = canvasSize(srcWidth.toFloat(), srcHeight.toFloat(), spec.turns)
        val w = (spec.crop.width * canvas.width).roundToInt().coerceAtLeast(1)
        val h = (spec.crop.height * canvas.height).roundToInt().coerceAtLeast(1)
        return w to h
    }

    /**
     * The affine map from source pixels to output pixels as [a, b, c, d, e, f], meaning
     * `x' = a*x + c*y + e` and `y' = b*x + d*y + f`.
     */
    fun compositeAffine(srcWidth: Int, srcHeight: Int, spec: CropRotateSpec): DoubleArray {
        val canvas = canvasSize(srcWidth.toFloat(), srcHeight.toFloat(), spec.turns)
        var m = Affine.translation(-srcWidth / 2.0, -srcHeight / 2.0)
        m = Affine.rotation(spec.turns * QUARTER).times(m)
        m = Affine.scale(if (spec.flipH) -1.0 else 1.0, if (spec.flipV) -1.0 else 1.0).times(m)
        m = Affine.rotation(spec.straightenDeg.toDouble()).times(m)
        m = Affine.translation(canvas.width / 2.0, canvas.height / 2.0).times(m)
        m = Affine.translation(-spec.crop.left * canvas.width.toDouble(), -spec.crop.top * canvas.height.toDouble()).times(m)
        return doubleArrayOf(m.a, m.b, m.c, m.d, m.e, m.f)
    }

    private fun centered(w: Float, h: Float) = CropRect(0.5f - w / 2f, 0.5f - h / 2f, 0.5f + w / 2f, 0.5f + h / 2f)

    private fun homothety(rect: CropRect, k: Float) = CropRect(
        0.5f + k * (rect.left - 0.5f),
        0.5f + k * (rect.top - 0.5f),
        0.5f + k * (rect.right - 0.5f),
        0.5f + k * (rect.bottom - 0.5f)
    )

    private fun lerp(from: CropRect, to: CropRect, t: Float) = CropRect(
        from.left + (to.left - from.left) * t,
        from.top + (to.top - from.top) * t,
        from.right + (to.right - from.right) * t,
        from.bottom + (to.bottom - from.bottom) * t
    )

    /** The furthest point from [from] (which fits) towards [to] that still fits. */
    private fun approach(from: CropRect, to: CropRect, canvas: CanvasSize, straightenDeg: Float): CropRect {
        if (fits(to, canvas, straightenDeg)) return to
        var lo = 0f
        var hi = 1f
        repeat(SEARCH_STEPS) {
            val mid = (lo + hi) / 2f
            if (fits(lerp(from, to, mid), canvas, straightenDeg)) lo = mid else hi = mid
        }
        return lerp(from, to, lo)
    }

    private fun freeCandidate(rect: CropRect, handle: CropHandle, dx: Float, dy: Float): CropRect {
        if (handle == CropHandle.MOVE) {
            return CropRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
        }
        var left = rect.left
        var right = rect.right
        var top = rect.top
        var bottom = rect.bottom
        if (handle in LEFT_HANDLES) left = min(left + dx, right - MIN_FRACTION)
        if (handle in RIGHT_HANDLES) right = max(right + dx, left + MIN_FRACTION)
        if (handle in TOP_HANDLES) top = min(top + dy, bottom - MIN_FRACTION)
        if (handle in BOTTOM_HANDLES) bottom = max(bottom + dy, top + MIN_FRACTION)
        return CropRect(left, top, right, bottom)
    }

    private fun lockedCandidate(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, aspect: Float, canvas: CanvasSize): CropRect {
        val minW = max(MIN_FRACTION * canvas.width, MIN_FRACTION * canvas.height * aspect)
        return when (handle) {
            CropHandle.LEFT, CropHandle.RIGHT -> {
                val grow = if (handle == CropHandle.RIGHT) dx else -dx
                val w = max((rect.width + grow) * canvas.width, minW)
                val nw = w / canvas.width
                val nh = w / aspect / canvas.height
                val top = rect.centerY - nh / 2f
                if (handle == CropHandle.RIGHT) {
                    CropRect(rect.left, top, rect.left + nw, top + nh)
                } else {
                    CropRect(rect.right - nw, top, rect.right, top + nh)
                }
            }
            CropHandle.TOP, CropHandle.BOTTOM -> {
                val grow = if (handle == CropHandle.BOTTOM) dy else -dy
                val w = max((rect.height + grow) * canvas.height * aspect, minW)
                val nw = w / canvas.width
                val nh = w / aspect / canvas.height
                val left = rect.centerX - nw / 2f
                if (handle == CropHandle.BOTTOM) {
                    CropRect(left, rect.top, left + nw, rect.top + nh)
                } else {
                    CropRect(left, rect.bottom - nh, left + nw, rect.bottom)
                }
            }
            else -> cornerCandidate(rect, handle, dx, dy, aspect, canvas, minW)
        }
    }

    private fun cornerCandidate(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, aspect: Float, canvas: CanvasSize, minW: Float): CropRect {
        val right = handle in RIGHT_HANDLES
        val bottom = handle in BOTTOM_HANDLES
        val anchorX = if (right) rect.left else rect.right
        val anchorY = if (bottom) rect.top else rect.bottom
        val pointX = (if (right) rect.right else rect.left) + dx
        val pointY = (if (bottom) rect.bottom else rect.top) + dy
        val widthPx = max((if (right) pointX - anchorX else anchorX - pointX) * canvas.width, 0f)
        val heightPx = max((if (bottom) pointY - anchorY else anchorY - pointY) * canvas.height, 0f)
        val w = max(max(widthPx, heightPx * aspect), minW)
        val nw = w / canvas.width
        val nh = w / aspect / canvas.height
        return CropRect(
            if (right) anchorX else anchorX - nw,
            if (bottom) anchorY else anchorY - nh,
            if (right) anchorX + nw else anchorX,
            if (bottom) anchorY + nh else anchorY
        )
    }

    /** A 2D affine transform, `x' = a*x + c*y + e`, `y' = b*x + d*y + f`. */
    private class Affine(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
        /** This transform applied after [o]. */
        fun times(o: Affine) = Affine(
            a * o.a + c * o.b,
            b * o.a + d * o.b,
            a * o.c + c * o.d,
            b * o.c + d * o.d,
            a * o.e + c * o.f + e,
            b * o.e + d * o.f + f
        )

        companion object {
            fun translation(x: Double, y: Double) = Affine(1.0, 0.0, 0.0, 1.0, x, y)

            fun scale(x: Double, y: Double) = Affine(x, 0.0, 0.0, y, 0.0, 0.0)

            /** Clockwise on a screen whose y axis points down; exact for multiples of 90 degrees. */
            fun rotation(degrees: Double): Affine {
                val turns = degrees / QUARTER
                val rounded = Math.rint(turns)
                if (turns == rounded) {
                    val q = ((rounded.toInt() % TURNS) + TURNS) % TURNS
                    val c = intArrayOf(1, 0, -1, 0)[q].toDouble()
                    val s = intArrayOf(0, 1, 0, -1)[q].toDouble()
                    return Affine(c, s, -s, c, 0.0, 0.0)
                }
                val c = cos(Math.toRadians(degrees))
                val s = sin(Math.toRadians(degrees))
                return Affine(c, s, -s, c, 0.0, 0.0)
            }
        }
    }
}
