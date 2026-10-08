package com.qtekfun.ultimategallery.feature.viewer

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.qtekfun.ultimategallery.R
import com.qtekfun.ultimategallery.core.ui.MediaThumb
import com.qtekfun.ultimategallery.core.ui.mediaSharedElement
import com.qtekfun.ultimategallery.domain.MediaItem

/**
 * A video page. Only the page being looked at owns a player (and plays); neighbours show their
 * poster frame so swiping stays cheap.
 */
@Composable
fun VideoPage(item: MediaItem, isCurrent: Boolean, modifier: Modifier = Modifier, previewTurns: Int = 0, revision: Int = 0) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isCurrent) {
            ActivePlayer(item, previewTurns, revision)
        } else {
            MediaThumb(
                item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().mediaSharedElement(item.id)
            )
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.viewer_play),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun ActivePlayer(item: MediaItem, previewTurns: Int, revision: Int) {
    val context = LocalContext.current
    val player = remember(item.id, revision) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    val degrees = animatedPreviewDegrees(previewTurns)
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
                useController = true
                controllerShowTimeoutMs = CONTROLLER_TIMEOUT_MS
                setShowNextButton(false)
                setShowPreviousButton(false)
                silenceHaptics(this)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { pageSize = it }
            .graphicsLayer {
                rotationZ = degrees
                val scale = fitScaleForRotation(pageSize.width.toFloat(), pageSize.height.toFloat(), degrees)
                scaleX = scale
                scaleY = scale
            }
            .mediaSharedElement(item.id)
    )
}

/** Views vibrate on their own; the player's controls must obey the app-wide haptics switch (off by default). */
internal fun silenceHaptics(view: android.view.View) {
    view.isHapticFeedbackEnabled = false
    (view as? ViewGroup)?.let { group -> for (i in 0 until group.childCount) silenceHaptics(group.getChildAt(i)) }
}

private const val CONTROLLER_TIMEOUT_MS = 2_500
