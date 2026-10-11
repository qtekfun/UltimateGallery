package com.qtekfun.ultimategallery.feature.wallpaper

import android.text.format.DateFormat
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.data.wallpaper.CropFraction
import com.qtekfun.ultimategallery.data.wallpaper.WallpaperGeometry
import com.qtekfun.ultimategallery.data.wallpaper.WallpaperTarget
import java.util.Date
import kotlin.math.max
import kotlin.math.min

private const val PREVIEW_PX = 3072
private val CLOCK_TOP = 72.dp

/** Framing of the photo in the screen-shaped frame: a zoom of at least 1 (cover) and a clamped pan. */
@Stable
class WallpaperFrameState {
    var zoom by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var imageSize by mutableStateOf(Size.Zero)
    var frameSize by mutableStateOf(IntSize.Zero)

    val ready: Boolean get() = imageSize.width > 0f && imageSize.height > 0f && frameSize.width > 0 && frameSize.height > 0

    /** A pinch of [zoomChange] around [centroid] (relative to the frame center) plus a [pan]. */
    fun applyGesture(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (!ready) return
        val newZoom = (zoom * zoomChange).coerceIn(1f, WallpaperGeometry.MAX_ZOOM)
        val moved = (offset - centroid) * (newZoom / zoom) + centroid + pan
        zoom = newZoom
        offset = clamp(moved)
    }

    fun reset() {
        zoom = 1f
        offset = Offset.Zero
    }

    fun crop(): CropFraction = WallpaperGeometry.crop(
        imageSize.width,
        imageSize.height,
        frameSize.width.toFloat(),
        frameSize.height.toFloat(),
        zoom,
        offset.x,
        offset.y
    )

    private fun clamp(o: Offset): Offset {
        val (mx, my) = WallpaperGeometry.maxOffset(imageSize.width, imageSize.height, frameSize.width.toFloat(), frameSize.height.toFloat(), zoom)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
}

/**
 * Sets a photo as the wallpaper: the photo fills a frame shaped like the screen (portrait), the user
 * pinches and drags to frame it, picks home, lock or both, and applies.
 */
@Composable
fun WallpaperScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: WallpaperViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val frame = remember { WallpaperFrameState() }
    var clock by rememberSaveable { mutableStateOf(false) }
    // Wallpapers are framed for the screen held upright, whatever the current rotation.
    val screen = remember {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        IntSize(min(bounds.width(), bounds.height()), max(bounds.width(), bounds.height()))
    }
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.results.collect { ok ->
            if (ok) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                // The screen closes at once, so the confirmation has to outlive it.
                Toast.makeText(context, R.string.wallpaper_done, Toast.LENGTH_SHORT).show()
                currentOnBack()
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                snackbar.showSnackbar(resources.getString(R.string.wallpaper_failed))
            }
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        state.item?.let { item ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .aspectRatio(screen.width.toFloat() / screen.height, matchHeightConstraintsFirst = true)
                    .clipToBounds()
                    .onSizeChanged { frame.frameSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { frame.reset() })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            frame.applyGesture(centroid - Offset(size.width / 2f, size.height / 2f), pan, zoom)
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(item.uri).size(PREVIEW_PX).build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    onSuccess = { frame.imageSize = it.painter.intrinsicSize },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = frame.zoom
                        scaleY = frame.zoom
                        translationX = frame.offset.x
                        translationY = frame.offset.y
                    }
                )
                if (clock) ClockPreview(Modifier.align(Alignment.TopCenter))
            }
        }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            TopControls(clock, onToggleClock = { clock = it }, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
            BottomControls(
                target = state.target,
                applying = state.applying,
                enabled = state.item != null,
                onTarget = viewModel::setTarget,
                onApply = {
                    if (frame.ready) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewModel.apply(frame.crop(), screen.width, screen.height)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TopControls(clock: Boolean, onToggleClock: (Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.wallpaper_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wallpaper_hint), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        }
        IconToggleButton(checked = clock, onCheckedChange = onToggleClock) {
            Icon(Icons.Outlined.Schedule, stringResource(if (clock) R.string.wallpaper_hide_clock else R.string.wallpaper_preview_clock))
        }
    }
}

@Composable
private fun BottomControls(
    target: WallpaperTarget,
    applying: Boolean,
    enabled: Boolean,
    onTarget: (WallpaperTarget) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = mapOf(
        WallpaperTarget.HOME to R.string.wallpaper_target_home,
        WallpaperTarget.LOCK to R.string.wallpaper_target_lock,
        WallpaperTarget.BOTH to R.string.wallpaper_target_both
    )
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WallpaperTarget.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == target,
                    onClick = { onTarget(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, WallpaperTarget.entries.size),
                    enabled = !applying
                ) { Text(stringResource(labels.getValue(option))) }
            }
        }
        Button(onClick = onApply, enabled = enabled && !applying, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (applying) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.wallpaper_applying))
            } else {
                Icon(Icons.Outlined.Wallpaper, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.wallpaper_apply))
            }
        }
    }
}

/** A lock-screen style clock over the frame, to keep faces and subjects clear of it. */
@Composable
private fun ClockPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = remember { Date() }
    val shadow = Shadow(Color(0x66000000), blurRadius = 12f)
    Column(
        modifier.fillMaxWidth().statusBarsPadding().padding(top = CLOCK_TOP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            DateFormat.getTimeFormat(context).format(now),
            style = MaterialTheme.typography.displayLarge.merge(TextStyle(shadow = shadow)),
            color = Color.White
        )
        Text(
            DateFormat.getLongDateFormat(context).format(now),
            style = MaterialTheme.typography.titleMedium.merge(TextStyle(shadow = shadow)),
            color = Color.White
        )
    }
}
