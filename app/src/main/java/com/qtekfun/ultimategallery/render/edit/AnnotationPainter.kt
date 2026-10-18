package com.qtekfun.ultimategallery.render.edit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.domain.edit.AnnotationGeometry
import com.qtekfun.ultimategallery.domain.edit.EditPoint
import com.qtekfun.ultimategallery.domain.edit.ShapeKind
import com.qtekfun.ultimategallery.domain.edit.StrokeStyle
import com.qtekfun.ultimategallery.render.MarkTypefaces
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the annotations of a photo edit on any canvas whose drawing area is the image, [width] x [height] pixels. The preview
 * (scaled to the screen) and the saved photo use this same code.
 */
class AnnotationPainter(private val typefaces: (String) -> Typeface?) : AnnotationGeometry.TextExtent {
    private val typefaceCache = HashMap<String, Typeface?>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, annotations: List<Annotation>, width: Int, height: Int) {
        annotations.forEach { draw(canvas, it, width, height) }
    }

    fun draw(canvas: Canvas, annotation: Annotation, width: Int, height: Int) {
        val short = min(width, height).toFloat()
        when (annotation) {
            is Annotation.Stroke -> drawStroke(canvas, annotation, width, height, short)
            is Annotation.Shape -> drawShape(canvas, annotation, width, height, short)
            is Annotation.Text -> drawText(canvas, annotation, width, height)
        }
    }

    override fun halfSize(text: Annotation.Text, width: Int, height: Int): Pair<Float, Float> {
        val layout = layout(text, width, height)
        val pad = if (text.boxed) padding(text, width, height) else 0f
        return (layout.width / 2f + pad) to (layout.height / 2f + pad)
    }

    private fun drawStroke(canvas: Canvas, stroke: Annotation.Stroke, width: Int, height: Int, short: Float) {
        if (stroke.points.isEmpty()) return
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(stroke.width * short, 1f)
        paint.color = stroke.color
        if (stroke.style == StrokeStyle.HIGHLIGHTER) {
            paint.alpha = (Color.alpha(stroke.color) * HIGHLIGHTER_ALPHA).toInt()
            paint.strokeCap = Paint.Cap.SQUARE
        } else {
            paint.strokeCap = Paint.Cap.ROUND
        }
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(smoothPath(stroke.points, width, height), paint)
    }

    private fun drawShape(canvas: Canvas, shape: Annotation.Shape, width: Int, height: Int, short: Float) {
        val x0 = shape.start.x * width
        val y0 = shape.start.y * height
        val x1 = shape.end.x * width
        val y1 = shape.end.y * height
        val strokeWidth = max(shape.width * short, 1f)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = shape.color
        paint.strokeWidth = strokeWidth
        when (shape.kind) {
            ShapeKind.LINE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(x0, y0, x1, y1, paint)
            }
            ShapeKind.ARROW -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.BUTT
                val (lx, ly) = AnnotationGeometry.arrowLineEnd(x0, y0, x1, y1, strokeWidth)
                canvas.drawLine(x0, y0, lx, ly, paint)
                val head = AnnotationGeometry.arrowHead(x0, y0, x1, y1, strokeWidth)
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = strokeWidth / 2f
                canvas.drawPath(
                    Path().apply {
                        moveTo(x1, y1)
                        lineTo(head[0].first, head[0].second)
                        lineTo(head[1].first, head[1].second)
                        close()
                    },
                    paint
                )
            }
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> {
                paint.style = if (shape.filled) Paint.Style.FILL else Paint.Style.STROKE
                paint.strokeJoin = Paint.Join.MITER
                val box = RectF(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
                if (shape.kind == ShapeKind.RECTANGLE) canvas.drawRect(box, paint) else canvas.drawOval(box, paint)
            }
        }
    }

    private fun drawText(canvas: Canvas, text: Annotation.Text, width: Int, height: Int) {
        val layout = layout(text, width, height)
        val cx = text.center.x * width
        val cy = text.center.y * height
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(text.rotationDeg)
        if (text.boxed) {
            val pad = padding(text, width, height)
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.color = boxColorFor(text.color)
            val box = RectF(-layout.width / 2f - pad, -layout.height / 2f - pad, layout.width / 2f + pad, layout.height / 2f + pad)
            canvas.drawRoundRect(box, pad, pad, paint)
        }
        canvas.translate(-layout.width / 2f, -layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun layout(text: Annotation.Text, width: Int, height: Int): StaticLayout {
        val size = max(text.size * min(width, height), 1f)
        val resolved = MarkTypefaces.resolve(text.fontId, ::typeface, BODY_WEIGHT, false, size)
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.textSize = size
        textPaint.typeface = resolved.typeface
        textPaint.color = text.color
        val content = text.text.ifEmpty { " " }
        val lineWidth = content.lines().maxOf { ceil(Layout.getDesiredWidth(it, textPaint)).toInt() }.coerceAtLeast(1)
        return StaticLayout.Builder.obtain(content, 0, content.length, textPaint, lineWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    private fun padding(text: Annotation.Text, width: Int, height: Int): Float = text.size * min(width, height) * BOX_PADDING

    private fun typeface(id: String): Typeface? = typefaceCache.getOrPut(id) { typefaces(id) }

    companion object {
        private const val HIGHLIGHTER_ALPHA = 0.4f
        private const val BODY_WEIGHT = 500
        private const val BOX_PADDING = 0.3f
        private const val LIGHT_TEXT = 0.55f

        /** A box that makes text of [color] readable: dark under light text, light under dark text. */
        fun boxColorFor(color: Int): Int {
            val luminance = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f
            return if (luminance > LIGHT_TEXT) Color.argb(170, 0, 0, 0) else Color.argb(220, 255, 255, 255)
        }

        /** A path through [points] (fractions of a [width] x [height] image), smoothed with quadratic curves through the midpoints. */
        fun smoothPath(points: List<EditPoint>, width: Int, height: Int): Path {
            val path = Path()
            if (points.isEmpty()) return path
            val first = points[0]
            path.moveTo(first.x * width, first.y * height)
            if (points.size == 1) {
                // A dot: a zero-length line still gets its round cap.
                path.lineTo(first.x * width + DOT_LENGTH, first.y * height)
                return path
            }
            for (i in 1 until points.size - 1) {
                val p = points[i]
                val n = points[i + 1]
                path.quadTo(p.x * width, p.y * height, (p.x + n.x) / 2f * width, (p.y + n.y) / 2f * height)
            }
            val last = points.last()
            path.lineTo(last.x * width, last.y * height)
            return path
        }

        private const val DOT_LENGTH = 0.01f
    }
}
