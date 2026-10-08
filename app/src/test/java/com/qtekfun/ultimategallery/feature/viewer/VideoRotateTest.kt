package com.qtekfun.ultimategallery.feature.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.video.VideoRotateResult
import com.qtekfun.ultimategallery.domain.video.VideoRotation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeRotation(
    var stored: Int? = 0,
    var result: VideoRotateResult = VideoRotateResult.Rotated(Uri.parse("content://media/1"), overwritten = true)
) : VideoRotation {
    val calls = mutableListOf<Pair<Int, SaveBehavior>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun currentRotation(item: MediaItem): Int? = stored

    override suspend fun rotate(item: MediaItem, quarterTurnsClockwise: Int, behavior: SaveBehavior): VideoRotateResult {
        calls += quarterTurnsClockwise to behavior
        gate?.await()
        return result
    }
}

private val video = MediaItem(1, Uri.EMPTY, "v", "video/mp4", true, 0, 10, 10, 2048, 0, 1, "DCIM/Camera/")

@OptIn(ExperimentalCoroutinesApi::class)
private fun controller(
    rotation: FakeRotation,
    behavior: SaveBehavior = SaveBehavior.COPY,
    remembered: MutableList<SaveBehavior> = mutableListOf()
): VideoRotateController {
    val scope = CoroutineScope(UnconfinedTestDispatcher())
    return VideoRotateController(rotation, { behavior }, { remembered += it }, scope)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoRotateControllerTest {
    @Test
    fun startPreviewsOneQuarterTurnAndTurnsAccumulate() {
        val c = controller(FakeRotation())
        c.start(video)
        assertEquals(1, c.state.value.turns)
        c.turn(1)
        c.turn(1)
        assertEquals(3, c.state.value.turns)
        c.turn(-1)
        assertEquals(2, c.state.value.turns)
    }

    @Test
    fun cancelResetsAndWritesNothing() {
        val fake = FakeRotation()
        val c = controller(fake)
        c.start(video)
        c.cancel()
        assertFalse(c.state.value.previewing)
        assertEquals(0, c.state.value.turns)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun unsupportedContainerEmitsMessageAndNoPreview() = runTest {
        val c = controller(FakeRotation(stored = null))
        c.start(video)
        assertFalse(c.state.value.previewing)
        assertEquals(VideoRotateEvent.Unsupported, c.events.first())
    }

    @Test
    fun saveUsesConfiguredBehaviorAndTurns() = runTest {
        val fake = FakeRotation()
        val c = controller(fake, SaveBehavior.OVERWRITE)
        c.start(video)
        c.turn(1)
        c.save()
        assertEquals(listOf(2 to SaveBehavior.OVERWRITE), fake.calls)
        assertEquals(VideoRotateEvent.Rotated(Uri.parse("content://media/1"), true), c.events.first())
        // Overwrite: the preview resets and the player is told to reload.
        assertEquals(VideoRotateState(revision = 1), c.state.value)
    }

    @Test
    fun copyDoesNotBumpRevision() = runTest {
        val fake = FakeRotation(result = VideoRotateResult.Rotated(Uri.parse("content://media/2"), overwritten = false))
        val c = controller(fake, SaveBehavior.COPY)
        c.start(video)
        c.save()
        assertEquals(0, c.state.value.revision)
        assertFalse(c.state.value.previewing)
        assertEquals(VideoRotateEvent.Rotated(Uri.parse("content://media/2"), false), c.events.first())
    }

    @Test
    fun fullTurnCannotBeSaved() {
        val fake = FakeRotation()
        val c = controller(fake)
        c.start(video)
        c.turn(3)
        assertFalse(c.state.value.canSave)
        c.save()
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun askShowsDialogStateAndRemembersChoice() {
        val fake = FakeRotation()
        val remembered = mutableListOf<SaveBehavior>()
        val c = controller(fake, SaveBehavior.ASK, remembered)
        c.start(video)
        c.save()
        assertTrue(c.state.value.asking)
        assertTrue(fake.calls.isEmpty())
        c.confirmAsk(SaveBehavior.COPY, remember = true)
        assertEquals(listOf(SaveBehavior.COPY), remembered)
        assertEquals(listOf(1 to SaveBehavior.COPY), fake.calls)
    }

    @Test
    fun dismissingAskKeepsPreview() {
        val c = controller(FakeRotation(), SaveBehavior.ASK)
        c.start(video)
        c.save()
        c.dismissAsk()
        assertFalse(c.state.value.asking)
        assertTrue(c.state.value.previewing)
    }

    @Test
    fun failureKeepsPreviewAndReportsEvent() = runTest {
        val c = controller(FakeRotation(result = VideoRotateResult.Denied))
        c.start(video)
        c.save()
        assertTrue(c.state.value.previewing)
        assertFalse(c.state.value.saving)
        assertEquals(VideoRotateEvent.Denied, c.events.first())
    }

    @Test
    fun cancelIsIgnoredWhileSaving() {
        val fake = FakeRotation().apply { gate = CompletableDeferred() }
        val c = controller(fake)
        c.start(video)
        c.save()
        assertTrue(c.state.value.saving)
        c.cancel()
        assertNotNull(c.state.value.item)
        fake.gate?.complete(Unit)
        assertNull(c.state.value.item)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w320dp-h640dp-xhdpi")
class VideoRotateUiTest {
    @get:Rule
    val rule = createComposeRule()

    private fun host(c: VideoRotateController) {
        rule.setContent {
            MaterialTheme {
                val state by c.state.collectAsState()
                if (state.previewing) {
                    VideoRotateOverlay(state, c)
                } else {
                    ViewerActionBar(video, ViewerActions({}, onRotate = { c.start(it) }), {}, {})
                }
            }
        }
    }

    @Test
    fun rotateButtonOpensPanelAndCancelRestoresBar() {
        val fake = FakeRotation()
        val c = controller(fake)
        host(c)
        rule.onNodeWithContentDescription("Rotate video").performClick()
        rule.onNodeWithContentDescription("Rotate left").assertExists()
        rule.onNodeWithContentDescription("Rotate right").performClick()
        assertEquals(2, c.state.value.turns)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, c.state.value.turns)
        rule.onNodeWithContentDescription("Rotate video").assertExists()
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun saveSendsTurnsAndBehaviorToTheFake() {
        val fake = FakeRotation()
        val c = controller(fake, SaveBehavior.COPY)
        host(c)
        rule.onNodeWithContentDescription("Rotate video").performClick()
        rule.onNodeWithContentDescription("Rotate left").performClick()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Rotate right").performClick()
        rule.onNodeWithContentDescription("Rotate right").performClick()
        rule.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(listOf(2 to SaveBehavior.COPY), fake.calls)
    }

    @Test
    fun askBehaviorShowsDialogWithThreeChoices() {
        val fake = FakeRotation()
        val c = controller(fake, SaveBehavior.ASK)
        host(c)
        rule.onNodeWithContentDescription("Rotate video").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Save a copy").assertExists()
        rule.onNodeWithText("Remember my choice").assertExists()
        rule.onNodeWithText("Overwrite original").performClick()
        assertEquals(listOf(1 to SaveBehavior.OVERWRITE), fake.calls)
    }

    @Test
    fun unsupportedVideoNeverOpensThePanel() {
        val c = controller(FakeRotation(stored = null))
        host(c)
        rule.onNodeWithContentDescription("Rotate video").performClick()
        rule.onNodeWithContentDescription("Rotate left").assertDoesNotExist()
    }

    @Test
    fun actionBarOffersRotateInsteadOfEditForVideos() {
        rule.setContent { ViewerActionBar(video, ViewerActions({}, {}, onRotate = {}), {}, {}) }
        rule.onNodeWithContentDescription("Rotate video").assertExists()
        rule.onNodeWithContentDescription("Edit").assertDoesNotExist()
    }

    @Test
    fun resultsMapToSnackbarTexts() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val uri = Uri.parse("content://media/2")
        assertEquals("Video rotated", rotateMessage(res, VideoRotateEvent.Rotated(uri, true)))
        assertEquals("Rotated copy saved", rotateMessage(res, VideoRotateEvent.Rotated(uri, false)))
        assertEquals("Not saved: changing the original needs your permission", rotateMessage(res, VideoRotateEvent.Denied))
        assertEquals("This video format can't be rotated losslessly", rotateMessage(res, VideoRotateEvent.Unsupported))
        assertEquals("Couldn't rotate the video: boom", rotateMessage(res, VideoRotateEvent.Failed("boom")))
    }

    @Test
    fun fitScaleShrinksAtQuarterTurnOnly() {
        assertEquals(1f, fitScaleForRotation(1080f, 2000f, 0f), 0.001f)
        assertEquals(1080f / 2000f, fitScaleForRotation(1080f, 2000f, 90f), 0.001f)
        assertEquals(1f, fitScaleForRotation(1080f, 2000f, 180f), 0.001f)
    }

    @Test
    fun aLandscapeVideoTurnedUprightFillsATallPage() {
        // 16:9 video on a 1080x2400 page: upright it is 1080x607; turned a quarter it can be 1080 wide x 1920 tall.
        assertEquals(1f, fitScaleForVideoRotation(1080f, 2400f, 16f / 9f, 0f), 0.001f)
        assertEquals(1080f / 607.5f, fitScaleForVideoRotation(1080f, 2400f, 16f / 9f, 90f), 0.01f)
        assertEquals(1f, fitScaleForVideoRotation(1080f, 2400f, 16f / 9f, 180f), 0.001f)
    }

    @Test
    fun aPortraitVideoTurnedSidewaysShrinksToFit() {
        val s = fitScaleForVideoRotation(1080f, 2400f, 9f / 16f, 90f)
        assertEquals(1080f / 1920f, s, 0.01f)
    }
}
