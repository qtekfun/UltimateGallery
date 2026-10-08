package com.qtekfun.ultimategallery.feature.files

import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.qtekfun.ultimategallery.domain.Folder
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
@Config(sdk = [34], qualifiers = "en-w400dp-h800dp-xhdpi")
class FileActionDialogsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun folder(id: Long, name: String, path: String) = Folder(id, name, path, 3, Uri.EMPTY, false, 0)

    private fun item(name: String, isVideo: Boolean = false) = MediaItem(
        1, Uri.EMPTY, name, if (isVideo) "video/mp4" else "image/jpeg", isVideo, 0, 10, 10, 2048, 0, 1, "DCIM/Camera/"
    )

    private val folders = listOf(
        folder(1, "Camera", "DCIM/Camera/"),
        folder(2, "Trips", "Pictures/Trips/"),
        folder(3, "Clips", "Movies/Clips/")
    )

    @Test
    fun pickerListsOnlyFoldersValidForImagesAndChoosesOnClick() {
        var chosen: String? = null
        rule.setContent { DestinationPickerContent(DestinationMode.MOVE, listOf(item("a.jpg")), folders) { chosen = it } }
        rule.onNodeWithText("Move to new folder").assertExists()
        rule.onNodeWithText("Camera").assertExists()
        rule.onNodeWithText("Movies/Clips").assertDoesNotExist()
        rule.onNodeWithText("Pictures/Trips").performClick()
        assertEquals("Pictures/Trips", chosen)
    }

    @Test
    fun pickerOffersMovieFoldersForVideos() {
        rule.setContent { DestinationPickerContent(DestinationMode.COPY, listOf(item("v.mp4", isVideo = true)), folders) {} }
        rule.onNodeWithText("Copy to new folder").assertExists()
        rule.onNodeWithText("Movies/Clips").assertExists()
    }

    @Test
    fun newFolderNeedsAValidName() {
        var chosen: String? = null
        rule.setContent { DestinationPickerContent(DestinationMode.MOVE, listOf(item("a.jpg")), folders) { chosen = it } }
        rule.onNodeWithText("Move to new folder").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextInput("a/b")
        rule.onNodeWithText("Move to new folder").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("Summer")
        rule.onNodeWithText("Move to new folder").assertIsEnabled().performClick()
        assertEquals("Pictures/Summer", chosen)
    }

    @Test
    fun renameIsPrefilledWithoutExtensionAndValidated() {
        var renamed: String? = null
        rule.setContent { RenameContent("IMG_0042.jpg") { renamed = it } }
        rule.onNodeWithText("IMG_0042").assertExists()
        rule.onNodeWithText("Extension: .jpg").assertExists()
        val field = hasSetTextAction()
        val button = hasText("Rename") and hasClickAction()
        rule.onNode(field).performTextClearance()
        rule.onNode(field).performTextInput("bad:name")
        rule.onNode(button).assertIsNotEnabled()
        rule.onNode(field).performTextClearance()
        rule.onNode(field).performTextInput("sunset")
        rule.onNode(button).assertIsEnabled().performClick()
        assertEquals("sunset", renamed)
    }

    @Test
    fun multiInfoShowsCountAndTotalSize() {
        rule.setContent { MultiInfoDialog(listOf(item("a.jpg"), item("b.jpg")), {}) }
        rule.onNodeWithText("2 items selected").assertExists()
        rule.onNodeWithText("Total size", substring = true).assertExists()
    }
}
