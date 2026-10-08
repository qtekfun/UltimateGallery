package com.qtekfun.ultimategallery.feature.export

import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportItemResult
import com.qtekfun.ultimategallery.domain.export.ExportResult
import com.qtekfun.ultimategallery.domain.export.ExportStatus
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
@Config(sdk = [34], qualifiers = "en-w400dp-h800dp-xhdpi")
class ExportProgressScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private class FakeController(val status: MutableStateFlow<ExportStatus>) : ExportController {
        var cancelled = false

        override fun start(itemIds: List<Long>, profile: WatermarkProfile) = "job"

        override fun observe(jobId: String): Flow<ExportStatus> = status

        override fun cancel(jobId: String) {
            cancelled = true
        }
    }

    private val media = object : MediaRepository {
        override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())

        override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = flowOf(emptyList())

        override suspend fun loadItems(ids: List<Long>): List<MediaItem> = emptyList()
    }

    private fun vm(controller: FakeController) = ExportProgressViewModel(SavedStateHandle(mapOf("jobId" to "job")), controller, media)

    @Test
    fun runningShowsCountAndCancelStopsTheJob() {
        val controller = FakeController(MutableStateFlow(ExportStatus.Running(2, 10, 0.5f, "IMG_1.jpg")))
        rule.setContent { ExportProgressScreen(onDone = {}, onOpenFolder = {}, viewModel = vm(controller)) }
        rule.onNodeWithText("3 of 10").assertExists()
        rule.onNodeWithText("IMG_1.jpg").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        assertTrue(controller.cancelled)
        rule.onNodeWithText("Stopping…").assertIsNotEnabled()
    }

    @Test
    fun finishedShowsTheSummaryWithShareEnabled() {
        val result = ExportResult(
            "job",
            listOf(
                ExportItemResult(1, Uri.parse("content://media/external/images/media/9"), "a.jpg", null),
                ExportItemResult(2, null, null, "decode failed")
            ),
            cancelled = false
        )
        val controller = FakeController(MutableStateFlow(ExportStatus.Finished(result)))
        var done = 0
        rule.setContent { ExportProgressScreen(onDone = { done++ }, onOpenFolder = {}, viewModel = vm(controller)) }
        rule.onNodeWithText("1 exported, 1 failed").assertExists()
        rule.onNodeWithText("Share all").assertIsEnabled()
        rule.onNode(hasText("decode failed", substring = true)).assertExists()
        rule.onAllNodes(hasText("Done") and hasClickAction())[0].performClick()
        assertEquals(1, done)
    }
}
