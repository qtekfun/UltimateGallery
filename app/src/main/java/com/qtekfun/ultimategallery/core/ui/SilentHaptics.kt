package com.qtekfun.ultimategallery.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Haptic feedback that does nothing; provided app-wide while haptics are turned off in Settings. */
object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** Makes every `LocalHapticFeedback` below this call a no-op unless [enabled]; follows the setting live. */
@Composable
fun ProvideHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val real = LocalHapticFeedback.current
    val haptics = remember(real, enabled) { if (enabled) real else SilentHaptics }
    CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
}
