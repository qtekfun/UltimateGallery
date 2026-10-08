package com.qtekfun.ultimategallery.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.core.theme.OverlineStyle
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.domain.MediaItem
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

private const val EDGE_SCROLL_MAX_PX = 28f
private const val MEDIA_CONTENT_TYPE = "media"
private const val HEADER_CONTENT_TYPE = "header"

/**
 * The photo grid of a folder: date headers, selection with long-press and drag (with auto scroll
 * near the edges) and a floating date chip while scrolling.
 */
@Composable
fun PhotoGrid(
    entries: List<GridEntry>,
    items: List<MediaItem>,
    columns: Int,
    selection: Set<Long>,
    selectionMode: Boolean,
    onOpen: (MediaItem) -> Unit,
    onToggle: (MediaItem) -> Unit,
    onSelectionChange: (Set<Long>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val haptics = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 80.dp.toPx() }
    val keyToIndex = remember(entries) {
        entries.filterIsInstance<GridEntry.Media>().associate { it.key to it.index }
    }
    val currentItems by rememberUpdatedState(items)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    var anchor by remember { mutableIntStateOf(-1) }
    var base by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var lastTouched by remember { mutableIntStateOf(-1) }

    fun indexAt(pos: Offset): Int? = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
        pos.x >= it.offset.x && pos.x < it.offset.x + it.size.width &&
            pos.y >= it.offset.y && pos.y < it.offset.y + it.size.height
    }?.key?.let { keyToIndex[it] }

    fun applyRange(index: Int) {
        if (anchor < 0 || index == lastTouched) return
        lastTouched = index
        val lo = min(anchor, index)
        val hi = max(anchor, index)
        val ids = LinkedHashSet(base)
        for (i in lo..hi) currentItems.getOrNull(i)?.let { ids.add(it.id) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        currentOnChange(ids)
    }

    fun endDrag() {
        anchor = -1
        lastTouched = -1
        dragPos = null
        autoScroll = 0f
    }

    LaunchedEffect(autoScroll != 0f) {
        while (autoScroll != 0f) {
            gridState.scrollBy(autoScroll)
            dragPos?.let { p -> indexAt(p)?.let(::applyRange) }
            delay(16)
        }
    }

    val floatingLabel by remember(entries) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            (first downTo 0).firstNotNullOfOrNull { entries.getOrNull(it) as? GridEntry.Header }
        }
    }

    Box(modifier) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(keyToIndex) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val idx = indexAt(pos)
                            if (idx != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                anchor = idx
                                base = currentSelection
                                dragPos = pos
                                lastTouched = -1
                                applyRange(idx)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPos = change.position
                            val height = size.height
                            autoScroll = when {
                                change.position.y < edgePx ->
                                    -EDGE_SCROLL_MAX_PX * (1f - change.position.y.coerceAtLeast(0f) / edgePx)
                                change.position.y > height - edgePx ->
                                    EDGE_SCROLL_MAX_PX * (1f - (height - change.position.y).coerceAtLeast(0f) / edgePx)
                                else -> 0f
                            }
                            indexAt(change.position)?.let(::applyRange)
                        },
                        onDragEnd = ::endDrag,
                        onDragCancel = ::endDrag
                    )
                },
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is GridEntry.Header -> item(
                        key = entry.key,
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = HEADER_CONTENT_TYPE
                    ) { DayHeader(entry) }
                    is GridEntry.Media -> item(key = entry.key, contentType = MEDIA_CONTENT_TYPE) {
                        MediaCell(
                            item = entry.item,
                            selected = entry.item.id in selection,
                            selectionMode = selectionMode,
                            onClick = { if (selectionMode) onToggle(entry.item) else onOpen(entry.item) }
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = gridState.isScrollInProgress && floatingLabel != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding() + 8.dp),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            floatingLabel?.let { h ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        dayText(h.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun dayText(label: DayLabel): String = when (label) {
    DayLabel.Today -> stringResource(R.string.day_today)
    DayLabel.Yesterday -> stringResource(R.string.day_yesterday)
    is DayLabel.Date -> label.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
}

@Composable
private fun DayHeader(header: GridEntry.Header) {
    Text(
        dayText(header.label).uppercase(),
        style = OverlineStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun MediaCell(item: MediaItem, selected: Boolean, selectionMode: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cell-scale"
    )
    val description = stringResource(if (item.isVideo) R.string.video_item else R.string.photo_item)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = description + " " + item.displayName
                this.selected = selected
            }
            .clickable(onClick = onClick)
    ) {
        MediaThumb(
            item.uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(if (selected) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall)
        )
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatDuration(item.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(22.dp)
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
