package com.qtekfun.ultimategallery.feature.viewer

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qtekfun.ultimategallery.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w320dp-h640dp-xhdpi")
class ViewerChromeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun item(isVideo: Boolean = false) = MediaItem(
        1, Uri.EMPTY, "a", if (isVideo) "video/mp4" else "image/jpeg", isVideo, 0, 10, 10, 2048, 0, 1, "DCIM/Camera/"
    )

    private val details = MediaDetails(
        name = "IMG_1.jpg", path = "DCIM/Camera/", dateMs = 0, sizeBytes = 2048, width = 4000, height = 3000, mimeType = "image/jpeg",
        durationMs = null, camera = "Pixel", lens = "Main", aperture = "f/1.8", exposure = "1/120", iso = "ISO 50", focalLength = "6 mm",
        latitude = 10.0, longitude = 20.0
    )

    private fun actions() = ViewerActions({}, {}, {}, {}, {}, {})

    @Test
    fun infoSheetShowsEveryRowAndBothMetadataButtons() {
        var shared = 0
        rule.setContent { InfoSheetContent(details, { shared++ }, {}) }
        listOf("Details", "IMG_1.jpg", "DCIM/Camera/", "image/jpeg", "Pixel", "Main").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("f/1.8", substring = true).assertExists()
        rule.onNodeWithText("Save copy without metadata").assertExists()
        rule.onNodeWithText("Share without metadata").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun infoSheetOmitsMetadataButtonsForVideos() {
        rule.setContent { InfoSheetContent(details.copy(mimeType = "video/mp4", durationMs = 1000), null, null) }
        rule.onNodeWithText("Details").assertIsDisplayed()
        rule.onNodeWithText("Share without metadata").assertDoesNotExist()
    }

    @Test
    fun actionBarHasOneLabelledInfoButtonAndNoWatermarkText() {
        rule.setContent { ViewerActionBar(item(), actions(), {}, {}) }
        rule.onAllNodesWithContentDescription("Info").assertCountEquals(1)
        rule.onNodeWithText("Watermark").assertDoesNotExist()
        rule.onNodeWithContentDescription("Watermark").assertExists()
        // Watermark, edit, share, delete, info and more: all icon buttons.
        rule.onAllNodes(hasClickAction()).assertCountEquals(6)
    }

    @Test
    fun actionBarHidesPhotoOnlyActionsForVideos() {
        rule.setContent { ViewerActionBar(item(isVideo = true), actions(), {}, {}) }
        rule.onNodeWithContentDescription("Watermark").assertDoesNotExist()
        rule.onNodeWithContentDescription("Edit").assertDoesNotExist()
        rule.onAllNodes(hasClickAction()).assertCountEquals(4)
    }
}
