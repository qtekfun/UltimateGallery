package com.qtekfun.ultimategallery.feature.gallery

import com.qtekfun.ultimategallery.domain.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The day a group of photos belongs to, relative to today. */
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Date(val date: LocalDate) : DayLabel
}

/** One cell-row of the folder grid: a date header spanning the full width, or a media cell. */
sealed interface GridEntry {
    val key: String

    data class Header(val day: LocalDate, val label: DayLabel, val count: Int) : GridEntry {
        override val key: String get() = "h$day"
    }

    data class Media(val item: MediaItem, val index: Int) : GridEntry {
        override val key: String get() = "m${item.id}"
    }
}

object GridEntries {
    fun labelFor(day: LocalDate, today: LocalDate): DayLabel = when (day) {
        today -> DayLabel.Today
        today.minusDays(1) -> DayLabel.Yesterday
        else -> DayLabel.Date(day)
    }

    /**
     * Interleaves date headers with [items], which must be newest first. [Media.index] is the item's
     * position in [items], independent of the headers, so selection ranges ignore them.
     */
    fun build(items: List<MediaItem>, zone: ZoneId, today: LocalDate): List<GridEntry> {
        if (items.isEmpty()) return emptyList()
        val days = items.map { Instant.ofEpochMilli(it.dateMs).atZone(zone).toLocalDate() }
        val counts = days.groupingBy { it }.eachCount()
        val out = ArrayList<GridEntry>(items.size + counts.size)
        var current: LocalDate? = null
        items.forEachIndexed { index, item ->
            val day = days[index]
            if (day != current) {
                current = day
                out += GridEntry.Header(day, labelFor(day, today), counts.getValue(day))
            }
            out += GridEntry.Media(item, index)
        }
        return out
    }
}
