package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import kotlin.math.roundToInt

private const val MIN_ASPECT = 0.4f
private const val MAX_ASPECT = 2.5f

/** Every photo of the batch with the watermark applied live; tap one to edit on it. */
@Composable
fun BatchStrip(
    items: List<MediaItem>,
    selectedIndex: Int,
    profile: WatermarkProfile,
    renderer: WatermarkRenderer,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().height(72.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val aspect = (item.width.toFloat() / item.height).coerceIn(MIN_ASPECT, MAX_ASPECT)
            val selected = index == selectedIndex
            val label = stringResource(R.string.batch_photo_label, index + 1, items.size)
            Box(
                Modifier
                    .height(72.dp)
                    .width((72 * aspect).dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(
                        BorderStroke(
                            if (selected) 3.dp else 0.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        MaterialTheme.shapes.small
                    )
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    }
                    .clickable { onSelect(index) }
            ) {
                MediaThumb(item.uri, contentDescription = null, modifier = Modifier.fillMaxSize())
                val orientation = Orientation.of(item.width, item.height)
                Canvas(Modifier.fillMaxSize()) {
                    renderer.draw(
                        drawContext.canvas.nativeCanvas,
                        size.width.roundToInt(),
                        size.height.roundToInt(),
                        profile,
                        orientation
                    )
                }
            }
        }
    }
}
