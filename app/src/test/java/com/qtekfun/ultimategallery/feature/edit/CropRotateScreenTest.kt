package com.qtekfun.ultimategallery.feature.edit

import android.net.Uri
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.qtekfun.ultimategallery.data.edit.CropRotateSpec
import com.qtekfun.ultimategallery.data.edit.EditSaver
import com.qtekfun.ultimategallery.data.edit.SaveResult
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w400dp-h800dp-xhdpi")
class CropRotateScreenTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val item = MediaItem(
        id = 5,
        uri = Uri.parse("content://media/external/images/media/5"),
        displayName = "IMG_5.jpg",
        mimeType = "image/jpeg",
        isVideo = false,
        dateMs = 1L,
        width = 400,
        height = 300,
        sizeBytes = 1L,
        durationMs = 0,
        bucketId = 1,
        relativePath = "DCIM/Camera/"
    )

    private val media = object : MediaRepository {
        override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())

        override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = flowOf(emptyList())

        override suspend fun loadItems(ids: List<Long>): List<MediaItem> = listOf(item)
    }

    private class FakeSaver : EditSaver {
        @Volatile var behavior: SaveBehavior? = null

        @Volatile var spec: CropRotateSpec? = null

        override suspend fun save(item: MediaItem, spec: CropRotateSpec, behavior: SaveBehavior): SaveResult {
            this.behavior = behavior
            this.spec = spec
            return SaveResult.Saved(Uri.parse("content://media/external/images/media/6"), overwritten = false)
        }
    }

    private val saver = FakeSaver()
    private lateinit var settings: SettingsRepository
    private lateinit var viewModel: CropRotateViewModel
    private var savedUri: Uri? = null

    private fun show(behavior: SaveBehavior) {
        settings = SettingsRepository(PreferenceDataStoreFactory.create { File(folder.root, "prefs.preferences_pb") })
        runBlocking { settings.setSaveBehavior(behavior) }
        viewModel = CropRotateViewModel(SavedStateHandle(mapOf("mediaId" to 5L)), media, settings, saver)
        rule.setContent {
            MaterialTheme { CropRotateScreen(onBack = {}, onSaved = { savedUri = it }, viewModel = viewModel) }
        }
    }

    @Test
    fun saveStartsDisabledAndRotateButtonsChangeTheState() {
        show(SaveBehavior.COPY)
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Rotate right").performClick()
        rule.waitForIdle()
        assertEquals(1, viewModel.state.value.spec.turns)
        rule.onNodeWithText("Save").assertIsEnabled()
        rule.onNodeWithContentDescription("Rotate left").performClick()
        rule.onNodeWithContentDescription("Rotate left").performClick()
        rule.waitForIdle()
        assertEquals(3, viewModel.state.value.spec.turns)
        rule.onNodeWithContentDescription("Flip horizontally").performClick()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.spec.flipH)
        rule.onNodeWithText("Reset").performClick()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.spec.isIdentity)
    }

    @Test
    fun aspectChipLocksTheFrameShape() {
        show(SaveBehavior.COPY)
        rule.onNodeWithText("1:1").performClick()
        rule.waitForIdle()
        val state = viewModel.state.value
        assertEquals(AspectRatio.SQUARE, state.aspect)
        val crop = state.spec.crop
        assertEquals(1f, crop.width * 400f / (crop.height * 300f), 1e-3f)
    }

    @Test
    fun saveWithACopyBehaviorCallsTheSaverWithoutAsking() {
        show(SaveBehavior.COPY)
        rule.onNodeWithContentDescription("Rotate right").performClick()
        rule.onNodeWithText("Save").performClick()
        awaitUntil { saver.behavior != null && savedUri != null }
        assertEquals(SaveBehavior.COPY, saver.behavior)
        assertEquals(1, saver.spec?.turns)
        assertNotNull(savedUri)
    }

    @Test
    fun askingOffersCopyOrOverwriteAndCanRememberTheChoice() {
        show(SaveBehavior.ASK)
        rule.onNodeWithContentDescription("Rotate right").performClick()
        rule.onNodeWithText("Save").performClick()
        awaitUntil { viewModel.state.value.askingBehavior }
        rule.onNodeWithText("Overwrite original").assertExists()
        rule.onNodeWithText("Remember my choice").performClick()
        rule.onNodeWithText("Save a copy").performClick()
        awaitUntil { saver.behavior != null && savedUri != null }
        assertEquals(SaveBehavior.COPY, saver.behavior)
        assertEquals(SaveBehavior.COPY, runBlocking { settings.settings.first().saveBehavior })
    }

    /** Waits for work done off the main thread (DataStore) and lets its results reach the main looper. */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met in time" }
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 20L
        const val TIMEOUT_MS = 5_000L
    }
}
