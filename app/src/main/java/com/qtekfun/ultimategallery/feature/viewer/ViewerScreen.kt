package com.qtekfun.ultimategallery.feature.viewer

import android.app.Activity
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.domain.MediaItem
import java.util.Date
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val DISMISS_VELOCITY = 1800f
private val DISMISS_DISTANCE = 140.dp

/** What the viewer can do with the current item. A null action is not offered. */
class ViewerActions(
    val onShare: (MediaItem) -> Unit,
    val onEdit: ((MediaItem) -> Unit)? = null,
    val onMove: ((MediaItem) -> Unit)? = null,
    val onCopy: ((MediaItem) -> Unit)? = null,
    val onRename: ((MediaItem) -> Unit)? = null,
    val onDelete: ((MediaItem) -> Unit)? = null
)

/** Full-screen viewer: swipe between items, zoom, swipe down to close, thumbnail strip and actions. */
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onWatermark: () -> Unit,
    onShare: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loaded = items
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (loaded == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (loaded.isEmpty()) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            ViewerContent(
                items = loaded,
                initialId = viewModel.initialMediaId,
                actions = ViewerActions(onShare = onShare),
                onBack = onBack,
                onWatermarkItem = {
                    viewModel.startWatermark(it)
                    onWatermark()
                },
                detailsOf = viewModel::details
            )
        }
    }
}

@Composable
private fun ViewerContent(
    items: List<MediaItem>,
    initialId: Long,
    actions: ViewerActions,
    onBack: () -> Unit,
    onWatermarkItem: (MediaItem) -> Unit,
    detailsOf: suspend (MediaItem) -> MediaDetails
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestItems by rememberUpdatedStateOf(items)
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0)) { latestItems.size }
    var overlays by rememberSaveable { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }
    val current = items.getOrNull(pager.currentPage)
    val dragging = dragY != 0f
    val screenHeightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toFloat()

    // Keep the same photo in view when the folder changes under the viewer (for example after a delete).
    val currentId = remember { mutableStateOf(current?.id) }
    LaunchedEffect(pager.settledPage) { items.getOrNull(pager.settledPage)?.let { currentId.value = it.id } }
    LaunchedEffect(items) {
        val id = currentId.value ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }

    ImmersiveEffect(hideBars = !overlays || dragging)
    BackHandler(enabled = showInfo) { showInfo = false }

    val backgroundAlpha = 1f - min(abs(dragY) / (screenHeightPx * 0.5f), 1f) * 0.85f
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backgroundAlpha))) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { latestItems.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1
        ) { page ->
            val item = latestItems.getOrNull(page) ?: return@HorizontalPager
            val isCurrent = page == pager.currentPage
            val zoom = remember(item.id) { ZoomState() }
            LaunchedEffect(isCurrent) { if (!isCurrent) zoom.reset() }
            Box(Modifier.fillMaxSize().offset { IntOffset(0, if (isCurrent) dragY.roundToInt() else 0) }) {
                if (item.isVideo) {
                    VideoPage(item, isCurrent)
                } else {
                    ZoomableImage(
                        item = item,
                        state = zoom,
                        onTap = { overlays = !overlays },
                        onDismissDrag = { dy -> dragY += dy },
                        onDismissEnd = { velocity ->
                            if (abs(dragY) > with(density) { DISMISS_DISTANCE.toPx() } || abs(velocity) > DISMISS_VELOCITY) {
                                onBack()
                            } else {
                                scope.launch {
                                    animate(dragY, 0f, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)) { v, _ -> dragY = v }
                                }
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = overlays && !dragging,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            if (current != null) TopBar(current, onBack) { showInfo = true }
        }
        AnimatedVisibility(
            visible = overlays && !dragging,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Column(
                Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).navigationBarsPadding()
            ) {
                ThumbnailStrip(items, pager.currentPage, onSelect = { scope.launch { pager.animateScrollToPage(it) } })
                if (current != null) ActionBar(current, actions, onWatermarkItem, onInfo = { showInfo = true })
            }
        }
    }

    if (showInfo && current != null) {
        val details by produceState<MediaDetails?>(null, current.id) { value = detailsOf(current) }
        InfoSheet(details = details, onDismiss = { showInfo = false })
    }
}

@Composable
private fun TopBar(item: MediaItem, onBack: () -> Unit, onInfo: () -> Unit) {
    val context = LocalContext.current
    val date = remember(item.dateMs) {
        DateFormat.getMediumDateFormat(context).format(Date(item.dateMs)) + "  " + DateFormat.getTimeFormat(context).format(Date(item.dateMs))
    }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
            ).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.viewer_close)) }
            Column(Modifier.weight(1f)) {
                Text(date, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(
                    item.displayName,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, stringResource(R.string.info)) }
        }
    }
}

@Composable
private fun ActionBar(item: MediaItem, actions: ViewerActions, onWatermark: (MediaItem) -> Unit, onInfo: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.isVideo) {
                Button(onClick = { onWatermark(item) }) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.watermark))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { actions.onShare(item) }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
                IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, stringResource(R.string.info)) }
            }
        }
    }
}

/** Hides the system bars while the viewer is in immersive mode and restores them on exit. */
@Composable
private fun ImmersiveEffect(hideBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(hideBars) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hideBars) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun <T> rememberUpdatedStateOf(value: T) = androidx.compose.runtime.rememberUpdatedState(value)
