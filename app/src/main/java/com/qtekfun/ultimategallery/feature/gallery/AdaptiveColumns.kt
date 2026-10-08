package com.qtekfun.ultimategallery.feature.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.max
import kotlin.math.roundToInt

private const val PHONE_WIDTH_DP = 420f

/**
 * Scales a phone column count to the window: the user's count describes a phone-wide screen, and
 * tablets, foldables and landscape get proportionally more columns so cells keep a similar size.
 */
fun adaptiveColumns(base: Int, widthDp: Float): Int = max(base, (base * widthDp / PHONE_WIDTH_DP).roundToInt())

@Composable
fun rememberAdaptiveColumns(base: Int): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current.density
    return adaptiveColumns(base, widthPx / density)
}
