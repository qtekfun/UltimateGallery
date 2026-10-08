package com.qtekfun.ultimategallery.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import com.qtekfun.ultimategallery.domain.watermark.MarkFont
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.TiledGeometry
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import kotlin.math.max

/**
 * Draws a watermark onto a canvas. This is the only place watermark pixels are produced: the live
 * editor, the batch thumbnails and the full-resolution export all call [draw], so they match.
 *
 * Every dimension is derived from fractions of the image size passed in, so the output at 400 px
 * wide is the same picture as at 4000 px wide.
 */
class WatermarkRenderer(private val bitmaps: (Uri) -> Bitmap?) {
    /** Draws the profile's mark for the orientation of an [imageWidth] x [imageHeight] image. */
    fun draw(canvas: Canvas, imageWidth: Int, imageHeight: Int, profile: WatermarkProfile, orientation: Orientation = Orientation.of(imageWidth, imageHeight)) =
        draw(canvas, imageWidth, imageHeight, profile.source, profile.opacity, profile.placementFor(orientation))

    fun draw(canvas: Canvas, imageWidth: Int, imageHeight: Int, source: WatermarkSource, opacity: Float, placement: Placement) {
        val mark = markFor(source.leaf()) ?: return
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val markWidth = placement.sizeFraction * w
        val markHeight = markWidth / mark.aspect
        val layer = canvas.saveLayerAlpha(0f, 0f, w, h, (opacity.coerceIn(0f, 1f) * ALPHA_MAX).toInt())
        if (source is WatermarkSource.Tiled) {
            TiledGeometry.tileCenters(
                w, h, markWidth, markHeight,
                placement.centerX * w, placement.centerY * h, placement.rotationDeg,
                source.spacing, source.staggerX, source.staggerY
            ).forEach { (x, y) -> drawMarkAt(canvas, mark, x, y, markWidth, placement.rotationDeg) }
        } else {
            drawMarkAt(canvas, mark, placement.centerX * w, placement.centerY * h, markWidth, placement.rotationDeg)
        }
        canvas.restoreToCount(layer)
    }

    /** Width over height of one mark, which the gestures need to compute its extents. */
    fun markAspect(source: WatermarkSource): Float = markFor(source.leaf())?.aspect ?: DEFAULT_ASPECT

    private fun drawMarkAt(canvas: Canvas, mark: Mark, x: Float, y: Float, width: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        mark.draw(canvas, width)
        canvas.restore()
    }

    private fun WatermarkSource.leaf(): WatermarkSource = if (this is WatermarkSource.Tiled) base.leaf() else this

    private fun markFor(source: WatermarkSource): Mark? = when (source) {
        is WatermarkSource.Image -> bitmaps(source.uri)?.let { ImageMark(it) }
        is WatermarkSource.Text -> source.text.takeIf { it.isNotBlank() }?.let { TextMark(it, source.style) }
        is WatermarkSource.Tiled -> markFor(source.base)
    }

    private interface Mark {
        val aspect: Float

        /** Draws the mark centered on the origin, scaled to [width]. */
        fun draw(canvas: Canvas, width: Float)
    }

    private class ImageMark(private val bitmap: Bitmap) : Mark {
        override val aspect = bitmap.width.toFloat() / bitmap.height
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas, width: Float) {
            val height = width / aspect
            canvas.drawBitmap(bitmap, null, RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), paint)
        }
    }

    /** Text laid out once at a reference size and scaled to the requested width. */
    private class TextMark(text: String, private val spec: TextStyleSpec) : Mark {
        private val lines = text.lines().filter { it.isNotEmpty() }.ifEmpty { listOf(text) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = REF_SIZE
            textAlign = Paint.Align.CENTER
            typeface = typefaceOf(spec)
            color = spec.colorArgb
        }
        private val metrics = fill.fontMetrics
        private val lineHeight = metrics.descent - metrics.ascent
        private val textWidth = lines.maxOf { fill.measureText(it) }
        private val pad = if (spec.backgroundEnabled) spec.backgroundPadding * lineHeight else 0f
        private val contentWidth = max(textWidth, 1f) + 2 * pad
        private val contentHeight = lineHeight * lines.size + 2 * pad
        override val aspect = contentWidth / contentHeight

        override fun draw(canvas: Canvas, width: Float) {
            val scale = width / contentWidth
            canvas.save()
            canvas.scale(scale, scale)
            if (spec.backgroundEnabled) {
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.backgroundColorArgb }
                val r = RectF(-contentWidth / 2, -contentHeight / 2, contentWidth / 2, contentHeight / 2)
                canvas.drawRoundRect(r, contentHeight / 2, contentHeight / 2, bg)
            }
            val firstBaseline = -contentHeight / 2 + pad - metrics.ascent
            lines.forEachIndexed { i, line ->
                val y = firstBaseline + i * lineHeight
                if (spec.outlineEnabled) {
                    val stroke = Paint(fill).apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = spec.outlineWidth * lineHeight
                        strokeJoin = Paint.Join.ROUND
                        color = spec.outlineColorArgb
                    }
                    canvas.drawText(line, 0f, y, stroke)
                }
                val paint = Paint(fill)
                if (spec.shadowEnabled) {
                    val radius = spec.shadowRadius * lineHeight
                    paint.setShadowLayer(radius.coerceAtLeast(MIN_SHADOW), 0f, radius / 2f, spec.shadowColorArgb)
                }
                canvas.drawText(line, 0f, y, paint)
            }
            canvas.restore()
        }

        private companion object {
            const val REF_SIZE = 100f
            const val MIN_SHADOW = 0.01f
        }
    }

    private companion object {
        const val ALPHA_MAX = 255
        const val DEFAULT_ASPECT = 3f

        fun typefaceOf(style: TextStyleSpec): Typeface {
            val family = when (style.font) {
                MarkFont.SANS -> "sans-serif"
                MarkFont.SERIF -> "serif"
                MarkFont.MONOSPACE -> "monospace"
                MarkFont.CURSIVE -> "cursive"
                MarkFont.CONDENSED -> "sans-serif-condensed"
            }
            return Typeface.create(Typeface.create(family, Typeface.NORMAL), style.weight, style.italic)
        }
    }
}
