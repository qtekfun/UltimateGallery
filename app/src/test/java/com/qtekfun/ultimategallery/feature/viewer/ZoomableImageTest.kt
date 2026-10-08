package com.qtekfun.ultimategallery.feature.viewer

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import com.qtekfun.ultimategallery.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ZoomableImageTest {
    @get:Rule
    val rule = createComposeRule()

    private val item = MediaItem(
        id = 1, uri = Uri.parse("content://media/external/images/media/1"), displayName = "a.jpg",
        mimeType = "image/jpeg", isVideo = false, dateMs = 0, width = 4000, height = 3000,
        sizeBytes = 1, durationMs = 0, bucketId = 1, relativePath = null
    )

    private class Events {
        var taps = 0
        var dragged = 0f
        var endVelocity: Float? = null
    }

    private fun show(state: ZoomState, events: Events) {
        rule.setContent {
            ZoomableImage(
                item = item,
                state = state,
                onTap = { events.taps++ },
                onDismissDrag = { events.dragged += it },
                onDismissEnd = { events.endVelocity = it }
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun doubleTapZoomsInAndAgainOut() {
        val state = ZoomState()
        show(state, Events())
        rule.onRoot().performTouchInput { doubleClick(Offset(400f, 800f)) }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertTrue("scale ${state.scale}", state.scale > 2f)
        rule.onRoot().performTouchInput { doubleClick(Offset(400f, 800f)) }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertEquals(1f, state.scale, 0.01f)
    }

    @Test
    fun singleTapIsReported() {
        val events = Events()
        show(ZoomState(), events)
        rule.onRoot().performTouchInput { click(Offset(400f, 800f)) }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(1, events.taps)
    }

    @Test
    fun verticalDragAtScaleOneIsADismissGesture() {
        val state = ZoomState()
        val events = Events()
        show(state, events)
        rule.onRoot().performTouchInput { swipe(Offset(400f, 600f), Offset(400f, 1100f), durationMillis = 300) }
        rule.waitForIdle()
        assertTrue("dragged ${events.dragged}", events.dragged > 300f)
        assertTrue(events.endVelocity != null)
        assertFalse(state.isZoomed)
    }

    @Test
    fun horizontalDragAtScaleOneIsLeftToThePager() {
        val events = Events()
        show(ZoomState(), events)
        rule.onRoot().performTouchInput { swipe(Offset(200f, 800f), Offset(600f, 800f), durationMillis = 300) }
        rule.waitForIdle()
        assertEquals(0f, events.dragged, 0f)
        assertEquals(null, events.endVelocity)
    }

    @Test
    fun pinchZoomsAndPanningIsBounded() {
        val state = ZoomState()
        show(state, Events())
        rule.onRoot().performTouchInput {
            pinch(Offset(350f, 800f), Offset(250f, 800f), Offset(450f, 800f), Offset(550f, 800f), durationMillis = 300)
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertTrue("scale ${state.scale}", state.scale > 1.5f)
        val max = state.maxOffset()
        assertTrue(kotlin.math.abs(state.offset.x) <= max.x + 0.5f)
        assertTrue(kotlin.math.abs(state.offset.y) <= max.y + 0.5f)
    }
}
