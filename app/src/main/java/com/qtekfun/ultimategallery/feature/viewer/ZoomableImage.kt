package com.qtekfun.ultimategallery.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.core.ui.mediaSharedElement
import com.qtekfun.ultimategallery.domain.MediaItem
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_TEXTURE_PX = 4096
private const val SHARPNESS = 3f
private const val VERTICAL_DOMINANCE = 1.4f

/**
 * One photo in the viewer: pinch and double-tap zoom, pan with inertia and bounds. Vertical drags at
 * scale 1 are reported through [onDismissDrag] and [onDismissEnd] (swipe down to close); horizontal
 * drags are left to the pager unless the zoomed photo can still pan.
 */
@Composable
fun ZoomableImage(
    item: MediaItem,
    state: ZoomState,
    onTap: () -> Unit,
    onDismissDrag: (dy: Float) -> Unit,
    onDismissEnd: (velocityY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentDrag by rememberUpdatedState(onDismissDrag)
    val currentEnd by rememberUpdatedState(onDismissEnd)

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { state.viewSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { pos ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        state.toggleZoom(scope, pos - center)
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    state.cancelAnimations()
                    val tracker = VelocityTracker()
                    var total = Offset.Zero
                    var axis = Axis.UNDECIDED
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.any { it.isConsumed }) break
                        val pointers = event.changes.count { it.pressed }
                        val pan = event.calculatePan()
                        if (pointers >= 2) {
                            pinched = true
                            val center = Offset(size.width / 2f, size.height / 2f)
                            state.applyGesture(event.calculateCentroid(useCurrent = true) - center, pan, event.calculateZoom())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!pinched) {
                            tracker.addPosition(event.changes.first().uptimeMillis, event.changes.first().position)
                            total += pan
                            if (state.isZoomed) {
                                val applied = state.panBy(pan)
                                // At a bound the pager may take the horizontal part; otherwise this gesture owns the drag.
                                val blockedAtEdge = abs(pan.x) > abs(pan.y) && applied.x == 0f && pan.x != 0f
                                if (!blockedAtEdge && (applied != Offset.Zero)) event.changes.forEach { it.consume() }
                            } else {
                                if (axis == Axis.UNDECIDED && total.getDistance() > slop) {
                                    axis = if (abs(total.y) > abs(total.x) * VERTICAL_DOMINANCE) Axis.VERTICAL else Axis.HORIZONTAL
                                }
                                if (axis == Axis.VERTICAL) {
                                    currentDrag(pan.y)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    when {
                        pinched -> state.settle(scope)
                        state.isZoomed -> {
                            val v = tracker.calculateVelocity()
                            state.fling(scope, Offset(v.x, v.y))
                        }
                        axis == Axis.VERTICAL -> currentEnd(tracker.calculateVelocity().y)
                    }
                }
            }
    ) {
        val view = state.viewSize
        val fit = fitSize(item, view)
        LaunchedEffect(fit) { state.contentSize = fit }
        if (fit.width > 0f) {
            val context = LocalContext.current
            val request = remember(item.uri, fit) {
                val w = min(fit.width * SHARPNESS, MAX_TEXTURE_PX.toFloat()).roundToInt().coerceAtLeast(1)
                val h = min(fit.height * SHARPNESS, MAX_TEXTURE_PX.toFloat()).roundToInt().coerceAtLeast(1)
                ImageRequest.Builder(context).data(item.uri).size(CoilSize(w, h)).build()
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(with(density) { fit.width.toDp() }, with(density) { fit.height.toDp() })
                    .graphicsLayer {
                        scaleX = state.scale
                        scaleY = state.scale
                        translationX = state.offset.x
                        translationY = state.offset.y
                    }
                    .mediaSharedElement(item.id)
            ) {
                MediaThumb(item.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                AsyncImage(
                    model = request,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private enum class Axis { UNDECIDED, VERTICAL, HORIZONTAL }

/** The size of [item] fitted inside [view], keeping its aspect ratio. */
internal fun fitSize(item: MediaItem, view: IntSize): Size {
    if (view.width <= 0 || view.height <= 0) return Size.Zero
    // Unscanned items report no size; show them as large as the view allows.
    if (item.width <= 0 || item.height <= 0) return Size(view.width.toFloat(), view.height.toFloat())
    val scale = min(view.width.toFloat() / item.width, view.height.toFloat() / item.height)
    return Size(item.width * scale, item.height * scale)
}
