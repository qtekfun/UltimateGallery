package com.qtekfun.ultimategallery.domain.edit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Builds the alpha mask of a background removal from the pixels of the photo, without any model: regions are grown by color
 * similarity and refined with brushes. The mask holds one byte per pixel, 255 (keep) to 0 (removed).
 */
@Suppress("TooManyFunctions")
object BackgroundMask {
    private const val OPAQUE: Byte = -1
    private const val CLEAR: Byte = 0
    private const val SEED_RADIUS = 2
    private const val MIN_DISTANCE = 6f
    private const val MAX_DISTANCE = 190f
    private const val MAX_FEATHER_FRACTION = 0.006f
    private const val BYTE_MASK = 0xFF

    /** A fully opaque mask for a [width] x [height] photo. */
    fun opaque(width: Int, height: Int): ByteArray = ByteArray(width * height) { OPAQUE }

    /** Applies [ops] in order to a new mask for [pixels] (ARGB, row major) and softens its edges by [feather]. */
    fun build(pixels: IntArray, width: Int, height: Int, ops: List<MaskOp>, feather: Float): ByteArray {
        val mask = opaque(width, height)
        ops.forEach { op ->
            when (op) {
                is MaskOp.Auto -> removeBorderBackground(pixels, width, height, op.tolerance, mask)
                is MaskOp.Seed -> removeRegion(pixels, width, height, op.point, op.tolerance, mask)
                is MaskOp.Brush -> paint(mask, width, height, op.points, op.width * min(width, height), if (op.restore) OPAQUE else CLEAR)
            }
        }
        val radius = featherRadius(feather, width, height)
        return if (radius > 0) blur(mask, width, height, radius) else mask
    }

    /** The blur radius, in pixels, of an edge softness of [feather] (0..1) on a [width] x [height] photo. */
    fun featherRadius(feather: Float, width: Int, height: Int): Int = (feather.coerceIn(0f, 1f) * MAX_FEATHER_FRACTION * max(width, height)).roundToInt()

    /** The color distance a [tolerance] of 0..1 accepts. */
    fun threshold(tolerance: Float): Float = MIN_DISTANCE + (MAX_DISTANCE - MIN_DISTANCE) * tolerance.coerceIn(0f, 1f)

    /** A perceptual RGB distance ("redmean"), 0 for equal colors and about 765 for black against white. */
    fun distance(a: Int, b: Int): Float {
        val r1 = (a shr 16) and BYTE_MASK
        val g1 = (a shr 8) and BYTE_MASK
        val b1 = a and BYTE_MASK
        val r2 = (b shr 16) and BYTE_MASK
        val g2 = (b shr 8) and BYTE_MASK
        val b2 = b and BYTE_MASK
        val rMean = (r1 + r2) / 2f
        val dr = (r1 - r2).toFloat()
        val dg = (g1 - g2).toFloat()
        val db = (b1 - b2).toFloat()
        return sqrt((2f + rMean / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rMean) / 256f) * db * db)
    }

    /** Clears the region of colors close to the area around [point] that is connected to it. */
    fun removeRegion(pixels: IntArray, width: Int, height: Int, point: EditPoint, tolerance: Float, mask: ByteArray) {
        val x = (point.x * width).toInt().coerceIn(0, width - 1)
        val y = (point.y * height).toInt().coerceIn(0, height - 1)
        val reference = averageAround(pixels, width, height, x, y)
        fill(pixels, width, height, listOf(y * width + x), reference, threshold(tolerance), mask)
    }

    /** Clears the background connected to the borders whose color is close to the median color of the border. */
    fun removeBorderBackground(pixels: IntArray, width: Int, height: Int, tolerance: Float, mask: ByteArray) {
        val border = borderIndices(width, height)
        val reference = medianColor(border.map { pixels[it] })
        val limit = threshold(tolerance)
        val seeds = border.filter { distance(pixels[it], reference) <= limit }
        fill(pixels, width, height, seeds, reference, limit, mask)
    }

    /** Sets every pixel within [diameter] / 2 of the polyline through [points] (fractions of the photo) to [value]. */
    fun paint(mask: ByteArray, width: Int, height: Int, points: List<EditPoint>, diameter: Float, value: Byte) {
        if (points.isEmpty()) return
        val radius = max(diameter / 2f, 0.5f)
        val step = max(radius / 2f, 1f)
        var prevX = points[0].x * width
        var prevY = points[0].y * height
        stamp(mask, width, height, prevX, prevY, radius, value)
        for (i in 1 until points.size) {
            val x = points[i].x * width
            val y = points[i].y * height
            val length = sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY))
            val steps = max(1, (length / step).toInt())
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                stamp(mask, width, height, prevX + (x - prevX) * t, prevY + (y - prevY) * t, radius, value)
            }
            prevX = x
            prevY = y
        }
    }

    /** Two passes of a box blur of [radius] in each direction: a soft, roughly Gaussian edge. */
    fun blur(mask: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        var current = mask
        repeat(2) {
            current = boxPass(current, width, height, radius, horizontal = true)
            current = boxPass(current, width, height, radius, horizontal = false)
        }
        return current
    }

    /** Multiplies the alpha of [pixels] by [mask] in place. */
    fun applyTo(pixels: IntArray, mask: ByteArray) {
        for (i in pixels.indices) {
            val m = mask[i].toInt() and BYTE_MASK
            if (m == BYTE_MASK) continue
            val p = pixels[i]
            val alpha = ((p ushr 24) * m + BYTE_MASK / 2) / BYTE_MASK
            pixels[i] = (alpha shl 24) or (p and 0x00FFFFFF)
        }
    }

    private fun fill(pixels: IntArray, width: Int, height: Int, seeds: List<Int>, reference: Int, limit: Float, mask: ByteArray) {
        val visited = BooleanArray(pixels.size)
        val stack = IntArray(pixels.size)
        var top = 0
        fun push(i: Int) {
            if (visited[i]) return
            visited[i] = true
            stack[top++] = i
        }
        seeds.forEach(::push)
        while (top > 0) {
            val index = stack[--top]
            if (distance(pixels[index], reference) > limit) continue
            mask[index] = CLEAR
            val x = index % width
            val y = index / width
            if (x > 0) push(index - 1)
            if (x < width - 1) push(index + 1)
            if (y > 0) push(index - width)
            if (y < height - 1) push(index + width)
        }
    }

    private fun averageAround(pixels: IntArray, width: Int, height: Int, cx: Int, cy: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (y in max(0, cy - SEED_RADIUS)..min(height - 1, cy + SEED_RADIUS)) {
            for (x in max(0, cx - SEED_RADIUS)..min(width - 1, cx + SEED_RADIUS)) {
                val p = pixels[y * width + x]
                r += (p shr 16) and BYTE_MASK
                g += (p shr 8) and BYTE_MASK
                b += p and BYTE_MASK
                n++
            }
        }
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    private fun borderIndices(width: Int, height: Int): List<Int> = buildList {
        for (x in 0 until width) {
            add(x)
            if (height > 1) add((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            add(y * width)
            if (width > 1) add(y * width + width - 1)
        }
    }

    private fun medianColor(colors: List<Int>): Int {
        fun median(shift: Int) = colors.map { (it shr shift) and BYTE_MASK }.sorted()[colors.size / 2]
        return (0xFF shl 24) or (median(16) shl 16) or (median(8) shl 8) or median(0)
    }

    private fun stamp(mask: ByteArray, width: Int, height: Int, cx: Float, cy: Float, radius: Float, value: Byte) {
        val r2 = radius * radius
        for (y in max(0, (cy - radius).toInt())..min(height - 1, (cy + radius).toInt())) {
            val dy = y + 0.5f - cy
            for (x in max(0, (cx - radius).toInt())..min(width - 1, (cx + radius).toInt())) {
                val dx = x + 0.5f - cx
                if (dx * dx + dy * dy <= r2) mask[y * width + x] = value
            }
        }
    }

    private fun boxPass(src: ByteArray, width: Int, height: Int, radius: Int, horizontal: Boolean): ByteArray {
        val out = ByteArray(src.size)
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val stride = if (horizontal) 1 else width
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            val start = if (horizontal) line * width else line
            fun at(i: Int) = src[start + i.coerceIn(0, length - 1) * stride].toInt() and BYTE_MASK
            var sum = 0
            for (i in -radius..radius) sum += at(i)
            for (i in 0 until length) {
                out[start + i * stride] = ((sum + window / 2) / window).toByte()
                sum += at(i + radius + 1) - at(i - radius)
            }
        }
        return out
    }
}
