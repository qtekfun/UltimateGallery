package com.qtekfun.ultimategallery.feature.gallery

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.qtekfun.ultimategallery.domain.MediaItem
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DRAG_STEPS = 40

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class PhotoGridSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 8)
    private val items = (0L until 6L).map { id ->
        MediaItem(
            id = id, uri = Uri.EMPTY, displayName = "$id.jpg", mimeType = "image/jpeg", isVideo = false,
            dateMs = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() - id, width = 10, height = 10,
            sizeBytes = 1, durationMs = 0, bucketId = 1, relativePath = null
        )
    }
    private var selection by mutableStateOf<Set<Long>>(emptySet())
    private val opened = mutableListOf<Long>()

    @Before
    fun setUp() {
        val entries = GridEntries.build(items, ZoneOffset.UTC, today)
        rule.setContent {
            PhotoGrid(
                entries = entries,
                items = items,
                columns = 3,
                selection = selection,
                selectionMode = selection.isNotEmpty(),
                onOpen = { opened += it.id },
                onToggle = { item -> selection = if (item.id in selection) selection - item.id else selection + item.id },
                onSelectionChange = { selection = it },
                onColumnsChange = {},
                contentPadding = PaddingValues()
            )
        }
    }

    private fun cell(id: Long) = rule.onNodeWithContentDescription("Photo $id.jpg")

    @Test
    fun releasingALongPressKeepsThePhotoSelected() {
        cell(2).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(setOf(2L), selection)
        assertEquals(emptyList<Long>(), opened)
    }

    @Test
    fun draggingAfterALongPressSelectsTheRangeAndKeepsItOnRelease() {
        val from = cell(1).getBoundsInRoot()
        val to = cell(4).getBoundsInRoot()
        val delta = with(rule.density) {
            Offset(((to.left + to.right - from.left - from.right) / 2).toPx(), ((to.top + to.bottom - from.top - from.bottom) / 2).toPx())
        }
        cell(1).performTouchInput {
            down(center)
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + 100)
            // Small steps like a real finger: one big jump would let the grid's own scroll claim the move.
            repeat(DRAG_STEPS) { moveBy(delta / DRAG_STEPS.toFloat()) }
            up()
        }
        rule.waitForIdle()
        assertEquals(setOf(1L, 2L, 3L, 4L), selection)
        assertEquals(emptyList<Long>(), opened)
    }

    @Test
    fun tapsStillOpenOrToggleAfterALongPress() {
        cell(0).performClick()
        assertEquals(listOf(0L), opened)

        cell(1).performTouchInput { longClick() }
        cell(3).performClick()
        assertEquals(setOf(1L, 3L), selection)
        cell(1).performClick()
        assertEquals(setOf(3L), selection)
        assertEquals(listOf(0L), opened)
    }
}
