package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.PlacementMath
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import kotlin.math.roundToInt

/**
 * The large canvas showing the current photo with the live watermark. One finger drags the mark,
 * two fingers scale and rotate it; the corner handle scales and rotates precisely.
 */
@Composable
fun EditorCanvas(
    item: MediaItem,
    profile: WatermarkProfile,
    orientation: Orientation,
    renderer: WatermarkRenderer,
    guideX: Float?,
    guideY: Float?,
    onGestureStart: () -> Unit,
    onGesture: (panX: Float, panY: Float, zoom: Float, rotationDeg: Float) -> Unit,
    onGestureEnd: () -> Unit,
    onHandleDrag: (sizeFraction: Float, rotationDeg: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageAspect = item.width.toFloat() / item.height
    val placement = profile.placementFor(orientation)
    val markAspect = renderer.markAspect(profile.source)
    val accent = MaterialTheme.colorScheme.primary
    val slop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(imageAspect)
                .clip(MaterialTheme.shapes.medium)
                .pointerInput(item.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onGestureStart()
                        var moved = false
                        var travelled = 0f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val cancelled = event.changes.any { it.isConsumed }
                            if (!cancelled) {
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                travelled += pan.getDistance()
                                if (!moved && (travelled > slop || zoom != 1f || rotation != 0f)) moved = true
                                if (moved) {
                                    onGesture(pan.x / size.width, pan.y / size.height, zoom, rotation)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (!cancelled && event.changes.any { it.pressed })
                        onGestureEnd()
                    }
                }
        ) {
            MediaThumb(item.uri, contentDescription = null, modifier = Modifier.fillMaxSize())
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawContext.canvas.let {
                    renderer.draw(it.nativeCanvas, w.roundToInt(), h.roundToInt(), profile, orientation)
                }
                drawSelectionFrame(placement, markAspect, w, h, accent)
                guideX?.let { drawLine(accent, Offset(it * w, 0f), Offset(it * w, h), strokeWidth = 2.dp.toPx()) }
                guideY?.let { drawLine(accent, Offset(0f, it * h), Offset(w, it * h), strokeWidth = 2.dp.toPx()) }
            }
            ScaleRotateHandle(
                placement,
                imageAspect,
                markAspect,
                accent,
                onHandleDrag,
                onGestureStart,
                onGestureEnd,
                density.density
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionFrame(placement: Placement, markAspect: Float, w: Float, h: Float, color: Color) {
    val markW = placement.sizeFraction * w
    val markH = markW / markAspect
    val center = Offset(placement.centerX * w, placement.centerY * h)
    rotate(placement.rotationDeg, pivot = center) {
        drawRect(
            color = color,
            topLeft = Offset(center.x - markW / 2, center.y - markH / 2),
            size = Size(markW, markH),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ScaleRotateHandle(
    placement: Placement,
    imageAspect: Float,
    markAspect: Float,
    color: Color,
    onDrag: (Float, Float) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    density: Float
) {
    val handleSize = 28.dp
    val touchSize = 48.dp
    val currentPlacement by rememberUpdatedState(placement)
    val (hx, hy) = PlacementMath.handlePosition(placement, imageAspect, markAspect)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val touchPx = touchSize.value * density
        Box(
            Modifier
                .offset { IntOffset((hx * wPx - touchPx / 2).roundToInt(), (hy * hPx - touchPx / 2).roundToInt()) }
                .size(touchSize)
                .pointerInput(imageAspect, markAspect) {
                    var pos = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            onStart()
                            val p = currentPlacement
                            val (x, y) = PlacementMath.handlePosition(p, imageAspect, markAspect)
                            pos = Offset(x * wPx, y * hPx)
                        },
                        onDragEnd = onEnd,
                        onDragCancel = onEnd
                    ) { change, drag ->
                        change.consume()
                        pos += drag
                        val p = currentPlacement
                        val (size, rot) = PlacementMath.handleToSizeAndRotation(
                            pos.x / wPx - p.centerX,
                            pos.y / hPx - p.centerY,
                            imageAspect,
                            markAspect
                        )
                        onDrag(size, rot)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(handleSize).clip(CircleShape).background(color))
            Box(Modifier.size(handleSize - 10.dp).clip(CircleShape).background(Color.White))
        }
    }
}
