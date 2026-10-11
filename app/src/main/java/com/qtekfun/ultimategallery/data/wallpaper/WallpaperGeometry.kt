package com.qtekfun.ultimategallery.data.wallpaper

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The part of a photo that becomes the wallpaper, as fractions (0..1) of its width and height. */
data class CropFraction(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val FULL = CropFraction(0f, 0f, 1f, 1f)
    }
}

/** How to decode a photo for a wallpaper: scale it to [targetWidth] x [targetHeight], then keep the given rectangle. */
data class DecodePlan(val targetWidth: Int, val targetHeight: Int, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Geometry of the wallpaper crop. The photo always covers a screen-shaped frame: at zoom 1 it is
 * scaled to cover it and centered, the user zooms in from there and pans within bounds. The offset
 * is the translation of the photo's center from the frame's center, in frame pixels.
 */
object WallpaperGeometry {
    const val MAX_ZOOM = 5f

    /** The scale at which an image of [imageW] x [imageH] just covers a [frameW] x [frameH] frame. */
    fun coverScale(imageW: Float, imageH: Float, frameW: Float, frameH: Float): Float = max(frameW / imageW, frameH / imageH)

    /** How far the photo may move from the center, on each axis, at [zoom] before an edge shows. */
    fun maxOffset(imageW: Float, imageH: Float, frameW: Float, frameH: Float, zoom: Float): Pair<Float, Float> {
        val s = coverScale(imageW, imageH, frameW, frameH) * zoom
        return max(0f, (imageW * s - frameW) / 2f) to max(0f, (imageH * s - frameH) / 2f)
    }

    /** The visible part of the photo for the given framing. */
    fun crop(imageW: Float, imageH: Float, frameW: Float, frameH: Float, zoom: Float, offsetX: Float, offsetY: Float): CropFraction {
        if (imageW <= 0f || imageH <= 0f || frameW <= 0f || frameH <= 0f) return CropFraction.FULL
        val s = coverScale(imageW, imageH, frameW, frameH) * zoom
        val shownW = imageW * s
        val shownH = imageH * s
        val left = ((shownW - frameW) / 2f - offsetX) / shownW
        val top = ((shownH - frameH) / 2f - offsetY) / shownH
        val width = frameW / shownW
        val height = frameH / shownH
        val l = left.coerceIn(0f, 1f - width)
        val t = top.coerceIn(0f, 1f - height)
        return CropFraction(l, t, min(1f, l + width), min(1f, t + height))
    }

    /**
     * Decodes only what the wallpaper needs: the photo ([sourceW] x [sourceH], orientation applied) is
     * scaled so that the [crop] just covers a [screenW] x [screenH] screen, never enlarged, and cut.
     */
    fun decodePlan(sourceW: Int, sourceH: Int, crop: CropFraction, screenW: Int, screenH: Int): DecodePlan {
        val cropW = max(1f, (crop.right - crop.left) * sourceW)
        val cropH = max(1f, (crop.bottom - crop.top) * sourceH)
        val scale = min(1f, max(screenW / cropW, screenH / cropH))
        val w = max(1, (sourceW * scale).roundToInt())
        val h = max(1, (sourceH * scale).roundToInt())
        val left = (crop.left * w).roundToInt().coerceIn(0, w - 1)
        val top = (crop.top * h).roundToInt().coerceIn(0, h - 1)
        val right = (crop.right * w).roundToInt().coerceIn(left + 1, w)
        val bottom = (crop.bottom * h).roundToInt().coerceIn(top + 1, h)
        return DecodePlan(w, h, left, top, right, bottom)
    }
}
