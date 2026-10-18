package com.qtekfun.ultimategallery.feature.photoedit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.domain.edit.AnnotationGeometry
import com.qtekfun.ultimategallery.domain.edit.EditPoint
import com.qtekfun.ultimategallery.render.edit.AnnotationPainter
import kotlin.math.min
import kotlin.math.roundToInt

private val STAGE_PAD = 12.dp
private val HIT_SLOP = 16.dp
private val CHECKER_CELL = 8.dp
private const val MIN_STEP_PX = 2f
private const val BRUSH_ALPHA = 0.45f

/** What the stage reports back to the editor. */
internal class StageActions(
    val onStroke: (List<EditPoint>) -> Unit,
    val previewStroke: (List<EditPoint>) -> Annotation.Stroke,
    val onShape: (EditPoint, EditPoint) -> Unit,
    val previewShape: (EditPoint, EditPoint) -> Annotation.Shape?,
    val onSelectAt: (EditPoint, Float) -> Long?,
    val onTransform: (dx: Float, dy: Float, zoom: Float, rotationDeg: Float) -> Unit,
    val onTransformEnd: () -> Unit,
    val onTap: (EditPoint) -> Unit,
    val onEditText: (Long) -> Unit
)

private enum class Gesture { NONE, FREEHAND, SHAPE, SELECT, TAP }

/** Where the photo sits on the stage: its top left corner and the stage pixels per preview pixel. */
private data class Fit(val left: Float, val top: Float, val scale: Float, val width: Int, val height: Int) {
    fun toPoint(p: Offset) = EditPoint((p.x - left) / (width * scale), (p.y - top) / (height * scale))

    fun contains(p: Offset) = p.x in left..left + width * scale && p.y in top..top + height * scale
}

/**
 * The photo being edited: the rendered base, the annotations drawn live with the same [painter] the saved photo uses, the
 * gesture in progress and the selection. A checkerboard shows through transparent parts. With [comparing], the untouched
 * photo is shown instead.
 */
@Composable
internal fun EditorStage(state: PhotoEditorState, painter: AnnotationPainter, comparing: Boolean, actions: StageActions, modifier: Modifier = Modifier) {
    val preview = state.preview ?: return
    val density = LocalDensity.current
    val pad = with(density) { STAGE_PAD.toPx() }
    val slop = with(density) { HIT_SLOP.toPx() }
    val shown = if (comparing) preview else state.base ?: preview
    val image = remember(shown) { shown.asImageBitmap() }
    val checker = rememberChecker(with(density) { CHECKER_CELL.toPx() })
    val selectionColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.photo_edit_stage)

    var livePoints by remember { mutableStateOf<List<EditPoint>?>(null) }
    var liveShape by remember { mutableStateOf<Pair<EditPoint, EditPoint>?>(null) }
    val gesture = gestureFor(state)
    val current by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)

    BoxWithConstraints(modifier) {
        val stageW = constraints.maxWidth.toFloat()
        val stageH = constraints.maxHeight.toFloat()
        val scale = min((stageW - 2 * pad) / preview.width, (stageH - 2 * pad) / preview.height).coerceAtLeast(0.01f)
        val fit = Fit((stageW - preview.width * scale) / 2f, (stageH - preview.height * scale) / 2f, scale, preview.width, preview.height)
        val currentFit by rememberUpdatedState(fit)

        val input = Modifier.pointerInput(gesture) {
            when (gesture) {
                Gesture.FREEHAND -> freehand({ currentFit }, { livePoints = it }) { currentActions.onStroke(it) }
                Gesture.SHAPE -> shape({ currentFit }, { liveShape = it }) { a, b -> currentActions.onShape(a, b) }
                Gesture.SELECT -> select({ currentFit }, { current }, { currentActions }, slop)
                Gesture.TAP -> detectTapGestures { p -> if (currentFit.contains(p)) currentActions.onTap(currentFit.toPoint(p)) }
                Gesture.NONE -> Unit
            }
        }

        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }.then(input)) {
            val w = preview.width * fit.scale
            val h = preview.height * fit.scale
            clipRect(fit.left, fit.top, fit.left + w, fit.top + h) {
                drawRect(checker, Offset(fit.left, fit.top), Size(w, h))
            }
            drawImage(
                image,
                dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = FilterQuality.Medium
            )
            if (!comparing) {
                val livePen = livePoints?.takeIf { state.tool == EditorTool.DRAW }?.let(actions.previewStroke)
                val liveBox = liveShape?.let { (a, b) -> actions.previewShape(a, b) }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(fit.left, fit.top)
                    native.scale(fit.scale, fit.scale)
                    native.clipRect(0f, 0f, preview.width.toFloat(), preview.height.toFloat())
                    painter.draw(native, state.edit.annotations, preview.width, preview.height)
                    livePen?.let { painter.draw(native, it, preview.width, preview.height) }
                    liveBox?.let { painter.draw(native, it, preview.width, preview.height) }
                    native.restore()
                }
                if (state.tool != EditorTool.DRAW) livePoints?.let { drawBrush(it, fit, brushWidth(state), brushColor(state)) }
                state.edit.annotation(state.selectedId)?.let { drawSelection(it, fit, painter, selectionColor) }
            }
        }
    }
}

private fun gestureFor(state: PhotoEditorState): Gesture = when {
    state.saving -> Gesture.NONE
    state.selecting -> Gesture.SELECT
    state.tool == EditorTool.DRAW && state.draw.mode in setOf(DrawMode.PEN, DrawMode.HIGHLIGHTER) -> Gesture.FREEHAND
    state.tool == EditorTool.DRAW -> Gesture.SHAPE
    state.tool == EditorTool.BLUR -> Gesture.FREEHAND
    state.tool == EditorTool.BACKGROUND && state.background.mode == BackgroundMode.TAP -> Gesture.TAP
    state.tool == EditorTool.BACKGROUND -> Gesture.FREEHAND
    else -> Gesture.NONE
}

private fun brushWidth(state: PhotoEditorState): Float = if (state.tool == EditorTool.BLUR) state.cover.width else state.background.brushWidth

private fun brushColor(state: PhotoEditorState): Color = when {
    state.tool == EditorTool.BACKGROUND && state.background.mode == BackgroundMode.RESTORE -> Color(0xFF43A047)
    state.tool == EditorTool.BACKGROUND -> Color(0xFFE53935)
    else -> Color.White
}

private suspend fun PointerInputScope.freehand(fit: () -> Fit, onLive: (List<EditPoint>?) -> Unit, onDone: (List<EditPoint>) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val points = mutableListOf(fit().toPoint(down.position))
        var last = down.position
        onLive(points.toList())
        drag(down.id) { change ->
            if ((change.position - last).getDistance() >= MIN_STEP_PX) {
                points += fit().toPoint(change.position)
                last = change.position
                onLive(points.toList())
            }
            change.consume()
        }
        onLive(null)
        onDone(points)
    }
}

private suspend fun PointerInputScope.shape(fit: () -> Fit, onLive: (Pair<EditPoint, EditPoint>?) -> Unit, onDone: (EditPoint, EditPoint) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val start = fit().toPoint(down.position)
        var end = start
        onLive(start to end)
        drag(down.id) { change ->
            end = fit().toPoint(change.position)
            onLive(start to end)
            change.consume()
        }
        onLive(null)
        onDone(start, end)
    }
}

/** Picks an item with one finger, moves it, and with two fingers scales and turns it. Tapping a selected text edits it. */
private suspend fun PointerInputScope.select(fit: () -> Fit, state: () -> PhotoEditorState, actions: () -> StageActions, slop: Float) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val f = fit()
        val before = state().selectedId
        val id = actions().onSelectAt(f.toPoint(down.position), slop / f.scale) ?: return@awaitEachGesture
        down.consume()
        var moved = false
        do {
            val event = awaitPointerEvent()
            val pan = event.calculatePan()
            val zoom = event.calculateZoom()
            val rotation = event.calculateRotation()
            if (pan != Offset.Zero || zoom != 1f || rotation != 0f) {
                if (pan.getDistance() > 0f || zoom != 1f || rotation != 0f) moved = true
                actions().onTransform(pan.x / (f.width * f.scale), pan.y / (f.height * f.scale), zoom, rotation)
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } while (event.changes.any { it.pressed })
        actions().onTransformEnd()
        if (!moved && before == id && state().edit.annotation(id) is Annotation.Text) actions().onEditText(id)
    }
}

private fun DrawScope.drawBrush(points: List<EditPoint>, fit: Fit, width: Float, color: Color) {
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { i, p ->
        val x = fit.left + p.x * fit.width * fit.scale
        val y = fit.top + p.y * fit.height * fit.scale
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (points.size == 1) path.lineTo(fit.left + points[0].x * fit.width * fit.scale + 0.1f, fit.top + points[0].y * fit.height * fit.scale)
    val stroke = width * min(fit.width, fit.height) * fit.scale
    drawPath(path, color.copy(alpha = BRUSH_ALPHA), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSelection(annotation: Annotation, fit: Fit, painter: AnnotationPainter, color: Color) {
    val b = AnnotationGeometry.bounds(annotation, fit.width, fit.height, painter)
    val cx = fit.left + b.centerX * fit.scale
    val cy = fit.top + b.centerY * fit.scale
    val margin = 4.dp.toPx()
    val hw = b.halfWidth * fit.scale + margin
    val hh = b.halfHeight * fit.scale + margin
    rotate(b.rotationDeg, Offset(cx, cy)) {
        val topLeft = Offset(cx - hw, cy - hh)
        val size = Size(2 * hw, 2 * hh)
        drawRect(Color.White, topLeft, size, style = Stroke(3.dp.toPx()))
        drawRect(color, topLeft, size, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
    }
}

@Composable
private fun rememberChecker(cell: Float): ShaderBrush = remember(cell) {
    val size = (cell * 2).roundToInt().coerceAtLeast(2)
    val half = size / 2
    val light = 0xFFE0E0E0.toInt()
    val dark = 0xFFBDBDBD.toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (y in 0 until size) for (x in 0 until size) bitmap.setPixel(x, y, if ((x < half) == (y < half)) light else dark)
    ShaderBrush(ImageShader(bitmap.asImageBitmap() as ImageBitmap, TileMode.Repeated, TileMode.Repeated))
}
