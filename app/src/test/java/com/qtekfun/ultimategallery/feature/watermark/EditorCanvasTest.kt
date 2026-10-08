package com.qtekfun.ultimategallery.feature.watermark

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import org.junit.Assert.assertEquals
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
class EditorCanvasTest {
    @get:Rule
    val rule = createComposeRule()

    private val item = MediaItem(
        id = 1, uri = Uri.parse("content://media/external/images/media/1"), displayName = "a.jpg",
        mimeType = "image/jpeg", isVideo = false, dateMs = 0, width = 400, height = 300,
        sizeBytes = 1, durationMs = 0, bucketId = 1, relativePath = null
    )

    private class Recorder {
        var starts = 0
        var ends = 0
        var panX = 0f
        var panY = 0f
        var zoom = 1f
        var rotation = 0f
    }

    private fun show(rec: Recorder) {
        rule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                EditorCanvas(
                    item = item,
                    profile = WatermarkProfile(),
                    orientation = Orientation.LANDSCAPE,
                    renderer = WatermarkRenderer { null },
                    guideX = null,
                    guideY = null,
                    onGestureStart = { rec.starts++ },
                    onGesture = { px, py, z, r ->
                        rec.panX += px
                        rec.panY += py
                        rec.zoom *= z
                        rec.rotation += r
                    },
                    onGestureEnd = { rec.ends++ },
                    onHandleDrag = { _, _ -> },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @Test
    fun oneFingerDragMovesTheMarkByTheDraggedFractionOfTheImage() {
        val rec = Recorder()
        show(rec)
        rule.onRoot().performTouchInput { swipe(Offset(100f, 150f), Offset(300f, 150f), durationMillis = 200) }
        rule.waitForIdle()
        assertEquals(1, rec.starts)
        assertEquals(1, rec.ends)
        // 200 px over a canvas 400 dp wide at xhdpi (2x) = 800 px wide: a quarter of the width.
        assertEquals(0.25f, rec.panX, 0.05f)
        assertEquals(0f, rec.panY, 0.02f)
        assertEquals(1f, rec.zoom, 0.01f)
    }

    @Test
    fun twoFingerPinchScalesTheMark() {
        val rec = Recorder()
        show(rec)
        rule.onRoot().performTouchInput {
            pinch(Offset(300f, 300f), Offset(250f, 300f), Offset(500f, 300f), Offset(550f, 300f), durationMillis = 200)
        }
        rule.waitForIdle()
        assertTrue("zoom was ${rec.zoom}", rec.zoom > 1.3f)
        assertEquals(0f, rec.rotation, 3f)
    }

    @Test
    fun twoFingerTwistRotatesTheMark() {
        val rec = Recorder()
        show(rec)
        rule.onRoot().performTouchInput {
            pinch(Offset(300f, 400f), Offset(350f, 313f), Offset(500f, 400f), Offset(450f, 487f), durationMillis = 200)
        }
        rule.waitForIdle()
        assertTrue("rotation was ${rec.rotation}", kotlin.math.abs(rec.rotation) > 10f)
    }

    @Test
    fun aTapDoesNotMoveTheMark() {
        val rec = Recorder()
        show(rec)
        rule.onRoot().performTouchInput { click(Offset(200f, 200f)) }
        rule.waitForIdle()
        assertEquals(0f, rec.panX, 0f)
        assertEquals(1f, rec.zoom, 0f)
    }
}
