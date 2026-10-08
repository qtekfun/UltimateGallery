package com.qtekfun.ultimategallery.feature.viewer

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Zoom and pan of one image in the viewer. [scale] 1 shows the image fitted in the view; the
 * [offset] is the translation of the zoomed content, always kept inside the bounds the zoom allows.
 */
@Stable
class ZoomState(private val maxScale: Float = DEFAULT_MAX_SCALE) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewSize by mutableStateOf(IntSize.Zero)

    /** Size of the image fitted into the view at scale 1. */
    var contentSize by mutableStateOf(Size.Zero)

    private var job: Job? = null

    val isZoomed: Boolean get() = scale > ZOOMED_THRESHOLD

    /** How far the content may be translated at [s] before an edge enters the view. */
    fun maxOffset(s: Float = scale): Offset = Offset(
        max(0f, (contentSize.width * s - viewSize.width) / 2f),
        max(0f, (contentSize.height * s - viewSize.height) / 2f)
    )

    fun clamp(o: Offset, s: Float = scale): Offset {
        val m = maxOffset(s)
        return Offset(o.x.coerceIn(-m.x, m.x), o.y.coerceIn(-m.y, m.y))
    }

    fun cancelAnimations() {
        job?.cancel()
    }

    /** Applies a live pinch: [zoom] around [centroid] (relative to the view center) plus [pan]. */
    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(MIN_OVERSHOOT_SCALE, maxScale * OVERSHOOT)
        val ratio = newScale / scale
        // Keep the point under the fingers fixed while scaling.
        val moved = (offset - centroid) * ratio + centroid + pan
        scale = newScale
        offset = if (newScale <= 1f) moved else rubberBand(moved, newScale)
    }

    /** Pans by [delta]; returns the part of it that was actually applied. */
    fun panBy(delta: Offset): Offset {
        val target = clamp(offset + delta)
        val applied = target - offset
        offset = target
        return applied
    }

    /** After a pinch: spring back inside the allowed scale and bounds. */
    fun settle(scope: CoroutineScope) {
        val targetScale = scale.coerceIn(1f, maxScale)
        val targetOffset = if (targetScale <= 1f) Offset.Zero else clamp(offset, targetScale)
        if (targetScale != scale || targetOffset != offset) animateTo(scope, targetScale, targetOffset)
    }

    fun fling(scope: CoroutineScope, velocity: Offset) {
        job?.cancel()
        val m = maxOffset()
        job = scope.launch {
            launch {
                AnimationState(offset.x, velocity.x).animateDecay(exponentialDecay<Float>()) {
                    offset = Offset(value.coerceIn(-m.x, m.x), offset.y)
                }
            }
            launch {
                AnimationState(offset.y, velocity.y).animateDecay(exponentialDecay<Float>()) {
                    offset = Offset(offset.x, value.coerceIn(-m.y, m.y))
                }
            }
        }
    }

    /** Double tap: zoom in around [tap] (relative to the view center), or back out when already zoomed. */
    fun toggleZoom(scope: CoroutineScope, tap: Offset) {
        if (isZoomed) {
            animateTo(scope, 1f, Offset.Zero)
        } else {
            val target = doubleTapScale()
            animateTo(scope, target, clamp(-tap * (target - 1f), target))
        }
    }

    fun reset() {
        job?.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    /** Zoom level of a double tap: fills the view when the image is narrower, otherwise 2.5x. */
    fun doubleTapScale(): Float {
        if (contentSize.width <= 0f || contentSize.height <= 0f) return DOUBLE_TAP_SCALE
        val fill = max(viewSize.width / contentSize.width, viewSize.height / contentSize.height)
        return max(DOUBLE_TAP_SCALE, fill).coerceAtMost(maxScale)
    }

    private fun animateTo(scope: CoroutineScope, targetScale: Float, targetOffset: Offset) {
        job?.cancel()
        val s0 = scale
        val o0 = offset
        job = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                scale = s0 + (targetScale - s0) * t
                offset = o0 + (targetOffset - o0) * t
            }
        }
    }

    /** Beyond the bounds the content resists: only a fraction of the overshoot is applied. */
    private fun rubberBand(o: Offset, s: Float): Offset {
        val m = maxOffset(s)
        fun axis(v: Float, limit: Float) = when {
            v > limit -> limit + (v - limit) * RESISTANCE
            v < -limit -> -limit + (v + limit) * RESISTANCE
            else -> v
        }
        return Offset(axis(o.x, m.x), axis(o.y, m.y))
    }

    companion object {
        const val DEFAULT_MAX_SCALE = 6f
        private const val ZOOMED_THRESHOLD = 1.02f
        private const val MIN_OVERSHOOT_SCALE = 0.7f
        private const val OVERSHOOT = 1.15f
        private const val DOUBLE_TAP_SCALE = 2.5f
        private const val RESISTANCE = 0.35f
    }
}
