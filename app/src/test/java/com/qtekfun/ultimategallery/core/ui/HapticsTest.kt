package com.qtekfun.ultimategallery.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HapticsTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    @Test
    fun hapticsAreSilentWhenDisabledAndFollowTheSettingLive() {
        val recorder = Recorder()
        var enabled by mutableStateOf(false)
        var fire: () -> Unit = {}
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                ProvideHaptics(enabled) {
                    val haptics = LocalHapticFeedback.current
                    fire = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                }
            }
        }
        rule.runOnIdle { fire() }
        assertEquals(0, recorder.count)
        enabled = true
        rule.waitForIdle()
        rule.runOnIdle { fire() }
        assertEquals(1, recorder.count)
        enabled = false
        rule.waitForIdle()
        rule.runOnIdle { fire() }
        assertEquals(1, recorder.count)
    }
}
