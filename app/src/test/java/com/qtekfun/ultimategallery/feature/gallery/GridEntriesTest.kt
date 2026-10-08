package com.qtekfun.ultimategallery.feature.gallery

import android.net.Uri
import com.qtekfun.ultimategallery.domain.MediaItem
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GridEntriesTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 8)

    private fun item(id: Long, day: LocalDate, hour: Int = 12) = MediaItem(
        id = id, uri = Uri.EMPTY, displayName = "$id.jpg", mimeType = "image/jpeg", isVideo = false,
        dateMs = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), width = 10, height = 10,
        sizeBytes = 1, durationMs = 0, bucketId = 1, relativePath = null
    )

    @Test
    fun labelsAreRelativeToToday() {
        assertEquals(DayLabel.Today, GridEntries.labelFor(today, today))
        assertEquals(DayLabel.Yesterday, GridEntries.labelFor(today.minusDays(1), today))
        assertEquals(DayLabel.Date(today.minusDays(2)), GridEntries.labelFor(today.minusDays(2), today))
    }

    @Test
    fun headersAreInsertedOncePerDayAndIndexesIgnoreThem() {
        val items = listOf(
            item(3, today, 15),
            item(2, today, 9),
            item(1, today.minusDays(1)),
            item(0, today.minusDays(9))
        )
        val entries = GridEntries.build(items, zone, today)
        assertEquals(7, entries.size)
        val headers = entries.filterIsInstance<GridEntry.Header>()
        assertEquals(listOf(2, 1, 1), headers.map { it.count })
        assertEquals(listOf(0, 1, 2, 3), entries.filterIsInstance<GridEntry.Media>().map { it.index })
        assertTrue(entries.first() is GridEntry.Header)
    }

    @Test
    fun emptyInputGivesNoEntries() {
        assertTrue(GridEntries.build(emptyList(), zone, today).isEmpty())
    }
}
