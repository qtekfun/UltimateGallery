package com.qtekfun.ultimategallery.feature.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.domain.MediaItem

private val ThumbWidth = 44.dp
private val CurrentWidth = 56.dp

/** Quick navigation within the folder: the current photo is centered and larger. */
@Composable
fun ThumbnailStrip(items: List<MediaItem>, currentIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val density = LocalDensity.current
    LaunchedEffect(currentIndex, items.size) {
        if (currentIndex in items.indices) {
            val viewport = state.layoutInfo.viewportSize.width
            val half = with(density) { (viewport / 2f - CurrentWidth.toPx() / 2f).toInt() }
            state.animateScrollToItem(currentIndex, scrollOffset = -half)
        }
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().height(64.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "thumb" }) { index, item ->
            val current = index == currentIndex
            MediaThumb(
                item.uri,
                contentDescription = null,
                modifier = Modifier
                    .width(if (current) CurrentWidth else ThumbWidth)
                    .height(if (current) 56.dp else 44.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .border(BorderStroke(if (current) 2.dp else 0.dp, Color.White), MaterialTheme.shapes.extraSmall)
                    .clickable { onSelect(index) }
            )
        }
    }
}
