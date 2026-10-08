package com.qtekfun.ultimategallery.domain.watermark

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Positions of the tiles of a tiled watermark, in image pixels. */
object TiledGeometry {
    const val MAX_TILES = 4000

    /**
     * Centers of the tiles covering an [imageWidth] x [imageHeight] image. The lattice is anchored so a
     * tile sits at ([anchorX], [anchorY]) and is rotated by [rotationDeg] around that anchor.
     */
    @Suppress("LongParameterList")
    fun tileCenters(
        imageWidth: Float,
        imageHeight: Float,
        markWidth: Float,
        markHeight: Float,
        anchorX: Float,
        anchorY: Float,
        rotationDeg: Float,
        spacing: Float,
        staggerX: Float,
        staggerY: Float
    ): List<Pair<Float, Float>> {
        val pitchX = markWidth * (1f + spacing.coerceAtLeast(0f))
        val pitchY = markHeight * (1f + spacing.coerceAtLeast(0f))
        if (pitchX <= 0f || pitchY <= 0f) return emptyList()
        val rad = Math.toRadians(rotationDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()

        // Bounds of the image in the lattice frame (inverse rotation about the anchor).
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((px, py) in listOf(0f to 0f, imageWidth to 0f, 0f to imageHeight, imageWidth to imageHeight)) {
            val dx = px - anchorX
            val dy = py - anchorY
            val lx = dx * c + dy * s
            val ly = -dx * s + dy * c
            minX = minOf(minX, lx)
            maxX = maxOf(maxX, lx)
            minY = minOf(minY, ly)
            maxY = maxOf(maxY, ly)
        }
        val margin = maxOf(markWidth, markHeight)
        val iMin = floor((minX - margin) / pitchX).toInt() - 1
        val iMax = ceil((maxX + margin) / pitchX).toInt() + 1
        val jMin = floor((minY - margin) / pitchY).toInt() - 1
        val jMax = ceil((maxY + margin) / pitchY).toInt() + 1

        val out = ArrayList<Pair<Float, Float>>()
        for (j in jMin..jMax) {
            for (i in iMin..iMax) {
                if (out.size >= MAX_TILES) return out
                val lx = i * pitchX + if (abs(j) % 2 == 1) staggerX * pitchX else 0f
                val ly = j * pitchY + if (abs(i) % 2 == 1) staggerY * pitchY else 0f
                val x = anchorX + lx * c - ly * s
                val y = anchorY + lx * s + ly * c
                // Keep tiles whose bounding circle can touch the image.
                val reach = margin
                if (x > -reach && x < imageWidth + reach && y > -reach && y < imageHeight + reach) out += x to y
            }
        }
        return out
    }
}
