package com.qtekfun.ultimategallery.feature.gallery

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pinch-to-resize for a grid, in the manner of iOS Photos: while the fingers move the grid scales
 * live; whenever the scale reaches what a different column count would look like, the column count
 * changes and the scale is rebased, so the resize feels continuous and ends snapped to a count.
 */
@Stable
class PinchResizeState(private val minColumns: Int, private val maxColumns: Int) {
    /** Live visual scale while pinching; springs back to 1 at the end. */
    var scale by mutableFloatStateOf(1f)
        private set

    /**
     * Applies a pinch [zoom] factor to [columns] and returns the (possibly changed) column count. Zooming
     * in (zoom > 1) means fewer, larger cells.
     */
    fun onZoom(columns: Int, zoom: Float): Int {
        var c = columns
        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        if (c > minColumns) {
            val ratio = c.toFloat() / (c - 1)
            if (scale >= 1f + (ratio - 1f) * TRIGGER) {
                c -= 1
                scale /= ratio
            }
        }
        if (c < maxColumns && c == columns) {
            val ratio = c.toFloat() / (c + 1)
            if (scale <= 1f - (1f - ratio) * TRIGGER) {
                c += 1
                scale /= ratio
            }
        }
        return c
    }

    fun release(scope: CoroutineScope) {
        scope.launch {
            animate(scale, 1f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { v, _ -> scale = v }
        }
    }

    private companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.2f
        const val TRIGGER = 0.6f
    }
}

/**
 * Makes a grid resizable by pinching. [columns] is the current count; [onColumnsChange] receives each
 * new count (persist it there). Single-finger gestures pass through, so scrolling is unaffected.
 */
@Composable
fun Modifier.pinchToResize(columns: Int, minColumns: Int, maxColumns: Int, onColumnsChange: (Int) -> Unit): Modifier {
    val state = remember(minColumns, maxColumns) { PinchResizeState(minColumns, maxColumns) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentColumns by rememberUpdatedState(columns)
    val currentChange by rememberUpdatedState(onColumnsChange)
    return this
        .pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pinching = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        pinching = true
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            val before = currentColumns
                            val after = state.onZoom(before, zoom)
                            if (after != before) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentChange(after)
                            }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (pinching) state.release(scope)
            }
        }
        .graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
        }
}
