package com.qtekfun.ultimategallery.domain.watermark

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure geometry for placing a watermark; no Android dependencies. */
object PlacementMath {
    const val MIN_SIZE = 0.03f
    const val MAX_SIZE = 1.5f
    private const val FULL_TURN = 360f
    private const val HALF_TURN = 180f

    /** Height of the mark as a fraction of the image height. */
    fun heightFraction(placement: Placement, imageAspect: Float, markAspect: Float): Float = placement.sizeFraction * imageAspect / markAspect

    /** Keeps the center inside the image, the size within bounds and the rotation in (-180, 180]. */
    fun clamp(p: Placement): Placement = Placement(
        centerX = p.centerX.coerceIn(0f, 1f),
        centerY = p.centerY.coerceIn(0f, 1f),
        sizeFraction = p.sizeFraction.coerceIn(MIN_SIZE, MAX_SIZE),
        rotationDeg = normalizeDegrees(p.rotationDeg)
    )

    fun normalizeDegrees(deg: Float): Float {
        var d = deg % FULL_TURN
        if (d > HALF_TURN) d -= FULL_TURN
        if (d <= -HALF_TURN) d += FULL_TURN
        return d
    }

    /**
     * Applies a gesture: [panX] and [panY] are fractions of the image width and height, [zoom] multiplies
     * the size, [rotationDeg] is added.
     */
    fun applyGesture(p: Placement, panX: Float, panY: Float, zoom: Float, rotationDeg: Float): Placement = clamp(
        Placement(
            centerX = p.centerX + panX,
            centerY = p.centerY + panY,
            sizeFraction = p.sizeFraction * zoom,
            rotationDeg = p.rotationDeg + rotationDeg
        )
    )

    /**
     * Half extents of the rotated mark's bounding box as fractions of the image width and height.
     */
    fun halfExtents(placement: Placement, imageAspect: Float, markAspect: Float): Pair<Float, Float> {
        val w = placement.sizeFraction
        val h = heightFraction(placement, imageAspect, markAspect)
        // Work in width-fraction units for both axes, then convert the vertical one to height units.
        val hInWidthUnits = h / imageAspect
        val rad = Math.toRadians(placement.rotationDeg.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val halfW = (w * c + hInWidthUnits * s) / 2f
        val halfHInWidthUnits = (w * s + hInWidthUnits * c) / 2f
        return halfW to halfHInWidthUnits * imageAspect
    }

    /** The scale (as a size fraction) and rotation a handle dragged to [dxFrac], [dyFrac] from the mark center implies. */
    fun handleToSizeAndRotation(dxFrac: Float, dyFrac: Float, imageAspect: Float, markAspect: Float): Pair<Float, Float> {
        // Vector from the center to the handle in width units.
        val x = dxFrac
        val y = dyFrac / imageAspect
        val dist = kotlin.math.sqrt(x * x + y * y)
        // The handle sits at the bottom-right corner; the diagonal angle of the unrotated mark offsets it.
        val hInWidth = 1f / markAspect
        val cornerAngle = Math.toDegrees(kotlin.math.atan2(hInWidth.toDouble(), 1.0)).toFloat()
        val angle = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
        val diagonal = kotlin.math.sqrt(1f + hInWidth * hInWidth)
        val size = (2f * dist / diagonal).coerceIn(MIN_SIZE, MAX_SIZE)
        return size to normalizeDegrees(angle - cornerAngle)
    }

    /** Where the bottom-right handle of the mark is, as image fractions. */
    fun handlePosition(placement: Placement, imageAspect: Float, markAspect: Float): Pair<Float, Float> {
        val halfW = placement.sizeFraction / 2f
        val halfH = placement.sizeFraction / markAspect / 2f
        val rad = Math.toRadians(placement.rotationDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val x = halfW * c - halfH * s
        val y = halfW * s + halfH * c
        return (placement.centerX + x) to (placement.centerY + y * imageAspect)
    }
}

/** Result of snapping: the adjusted placement plus the guides to show. */
data class SnapResult(
    val placement: Placement,
    /** x of a vertical guide line, as a fraction of the width, or null. */
    val guideX: Float?,
    /** y of a horizontal guide line, as a fraction of the height, or null. */
    val guideY: Float?,
    val rotationSnapped: Boolean
) {
    val isSnapping: Boolean get() = guideX != null || guideY != null || rotationSnapped
}

object Snapper {
    /** Snap distance as a fraction of the image width. */
    const val THRESHOLD = 0.018f
    private const val ROTATION_THRESHOLD_DEG = 3.5f
    private const val ROTATION_DETENT_DEG = 45f

    fun snap(placement: Placement, imageAspect: Float, markAspect: Float, margin: Float, enabled: Boolean): SnapResult {
        if (!enabled) return SnapResult(placement, null, null, false)
        val (halfW, halfH) = PlacementMath.halfExtents(placement, imageAspect, markAspect)
        val marginY = margin * imageAspect

        val xTargets = listOf(
            0.5f to 0.5f,
            halfW to 0f,
            1f - halfW to 1f,
            margin + halfW to margin,
            1f - margin - halfW to 1f - margin
        )
        val yTargets = listOf(
            0.5f to 0.5f,
            halfH to 0f,
            1f - halfH to 1f,
            marginY + halfH to marginY,
            1f - marginY - halfH to 1f - marginY
        )
        val (cx, gx) = nearest(placement.centerX, xTargets, THRESHOLD)
        val (cy, gy) = nearest(placement.centerY, yTargets, THRESHOLD * imageAspect)

        val detent = (placement.rotationDeg / ROTATION_DETENT_DEG).roundToInt() * ROTATION_DETENT_DEG
        val rotationSnapped = abs(placement.rotationDeg - detent) < ROTATION_THRESHOLD_DEG
        val rotation = if (rotationSnapped) detent else placement.rotationDeg

        val snapped = PlacementMath.clamp(placement.copy(centerX = cx, centerY = cy, rotationDeg = rotation))
        return SnapResult(snapped, gx, gy, rotationSnapped)
    }

    /** Returns the snapped value and the guide position, or the input and null when nothing is close. */
    private fun nearest(value: Float, targets: List<Pair<Float, Float>>, threshold: Float): Pair<Float, Float?> {
        var best: Pair<Float, Float>? = null
        var bestDistance = threshold
        for (t in targets) {
            val d = abs(value - t.first)
            if (d < bestDistance) {
                bestDistance = d
                best = t
            }
        }
        return best?.let { it.first to it.second } ?: (value to null)
    }
}
