package com.qtekfun.ultimategallery.render.edit

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.qtekfun.ultimategallery.domain.edit.BackgroundFill
import com.qtekfun.ultimategallery.domain.edit.BackgroundMask
import com.qtekfun.ultimategallery.domain.edit.ColorMatrices
import com.qtekfun.ultimategallery.domain.edit.Cover
import com.qtekfun.ultimategallery.domain.edit.CoverMode
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.domain.edit.PixelOps
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The single render path of the photo editor. [renderBase] applies everything but the annotations; [render] adds them with
 * [painter]. The preview renders the base of a small copy and draws the annotations live with the same [painter]; saving
 * renders the full-resolution photo.
 */
class PhotoEditRenderer(typefaces: (String) -> Typeface?) {
    val painter = AnnotationPainter(typefaces)

    /** A new bitmap with the whole [edit] applied to [src]; [src] is left untouched. */
    fun render(src: Bitmap, edit: PhotoEdit): Bitmap {
        val out = renderBase(src, edit)
        if (edit.annotations.isNotEmpty()) painter.draw(Canvas(out), edit.annotations, out.width, out.height)
        return out
    }

    /** A new bitmap with [edit] applied to [src] except its annotations. */
    fun renderBase(src: Bitmap, edit: PhotoEdit): Bitmap {
        var out = colored(src, edit)
        if (edit.adjust.sharpness > 0f) out = sharpened(out, edit.adjust.sharpness).also { out.recycle() }
        if (edit.adjust.vignette > 0f) vignette(out, edit.adjust.vignette)
        if (edit.covers.isNotEmpty()) covers(out, edit.covers)
        if (!edit.background.isIdentity) background(src, out, edit)
        return out
    }

    private fun colored(src: Bitmap, edit: PhotoEdit): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrices.forEdit(edit.adjust, edit.filter)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (!ColorMatrices.isIdentity(matrix)) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun sharpened(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var from = 0
        while (from < h) {
            val to = min(from + BAND_ROWS, h)
            val readFrom = max(from - 1, 0)
            val readTo = min(to + 1, h)
            val rows = IntArray((readTo - readFrom) * w)
            src.getPixels(rows, 0, w, 0, readFrom, w, readTo - readFrom)
            val band = PixelOps.sharpen(rows, w, readTo - readFrom, from - readFrom, to - readFrom, amount)
            out.setPixels(band, 0, w, 0, from, w, to - from)
            from = to
        }
        return out
    }

    private fun vignette(out: Bitmap, amount: Float) {
        val cx = out.width / 2f
        val cy = out.height / 2f
        val radius = hypot(cx, cy)
        val edge = Color.argb((amount.coerceIn(0f, 1f) * VIGNETTE_MAX_ALPHA).roundToInt(), 0, 0, 0)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, edge)
        paint.shader = RadialGradient(cx, cy, radius, colors, floatArrayOf(0f, VIGNETTE_START, 1f), Shader.TileMode.CLAMP)
        Canvas(out).drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
    }

    private fun covers(out: Bitmap, covers: List<Cover>) {
        val canvas = Canvas(out)
        val short = min(out.width, out.height).toFloat()
        // Every cover reads the photo as it was before the first cover, so overlapping strokes don't compound.
        val sources = HashMap<Pair<CoverMode, Float>, Bitmap>()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pending = covers.map { cover ->
            val key = cover.mode to cover.strength
            val source = sources.getOrPut(key) { coverSource(out, cover.mode, cover.strength) }
            cover to source
        }
        pending.forEach { (cover, source) ->
            paint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            paint.strokeWidth = max(cover.width * short, 1f)
            canvas.drawPath(AnnotationPainter.smoothPath(cover.points, out.width, out.height), paint)
        }
        sources.values.forEach { it.recycle() }
    }

    /** A blurred or pixelated copy of [src] the size of [src]. */
    private fun coverSource(src: Bitmap, mode: CoverMode, strength: Float): Bitmap {
        val short = min(src.width, src.height).toFloat()
        val s = strength.coerceIn(0f, 1f)
        val cell = when (mode) {
            CoverMode.BLUR -> short * (BLUR_MIN + (BLUR_MAX - BLUR_MIN) * s)
            CoverMode.PIXELATE -> short * (PIXEL_MIN + (PIXEL_MAX - PIXEL_MIN) * s)
        }.coerceAtLeast(2f)
        val smallW = max(1, (src.width / cell).roundToInt())
        val smallH = max(1, (src.height / cell).roundToInt())
        val small = downscale(src, smallW, smallH)
        val big = Bitmap.createScaledBitmap(small, src.width, src.height, mode == CoverMode.BLUR)
        if (small !== big) small.recycle()
        return big
    }

    private fun background(src: Bitmap, out: Bitmap, edit: PhotoEdit) {
        val bg = edit.background
        // The mask is grown on the untouched photo, at most MASK_PIXELS large, then stretched with filtering over the result.
        val scale = min(1f, sqrt(MASK_PIXELS / (src.width.toFloat() * src.height)))
        val mw = max(1, (src.width * scale).roundToInt())
        val mh = max(1, (src.height * scale).roundToInt())
        val small = if (mw == src.width && mh == src.height) src else downscale(src, mw, mh)
        val pixels = IntArray(mw * mh)
        small.getPixels(pixels, 0, mw, 0, 0, mw, mh)
        if (small !== src) small.recycle()
        val mask = BackgroundMask.build(pixels, mw, mh, bg.ops, bg.feather)
        for (i in pixels.indices) pixels[i] = (mask[i].toInt() and BYTE_MASK) shl ALPHA_SHIFT
        val alpha = Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        canvas.drawBitmap(alpha, null, android.graphics.Rect(0, 0, out.width, out.height), paint)
        alpha.recycle()
        val fill = bg.fill
        if (fill is BackgroundFill.Solid) canvas.drawColor(fill.color, PorterDuff.Mode.DST_OVER)
    }

    companion object {
        private const val BAND_ROWS = 256
        private const val VIGNETTE_MAX_ALPHA = 200
        private const val VIGNETTE_START = 0.45f
        private const val BLUR_MIN = 0.01f
        private const val BLUR_MAX = 0.05f
        private const val PIXEL_MIN = 0.015f
        private const val PIXEL_MAX = 0.06f
        private const val MASK_PIXELS = 4_000_000f
        private const val BYTE_MASK = 0xFF
        private const val ALPHA_SHIFT = 24

        /** [src] scaled down to [width] x [height] by halving first, so every source pixel counts (a plain bilinear scale skips most). */
        fun downscale(src: Bitmap, width: Int, height: Int): Bitmap {
            var current = src
            while (current.width / 2 >= width && current.height / 2 >= height) {
                val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
                if (current !== src) current.recycle()
                current = half
            }
            if (current.width == width && current.height == height) return if (current === src) src.copy(Bitmap.Config.ARGB_8888, true) else current
            val scaled = Bitmap.createScaledBitmap(current, width, height, true)
            if (current !== src && current !== scaled) current.recycle()
            return scaled
        }
    }
}
