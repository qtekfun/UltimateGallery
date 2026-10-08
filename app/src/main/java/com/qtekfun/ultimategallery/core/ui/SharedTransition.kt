package com.qtekfun.ultimategallery.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Key shared by a grid thumbnail and the viewer page of the same media item. */
fun mediaSharedKey(id: Long) = "media-$id"

/**
 * Marks a composable as the shared element for media item [id], so it flies between the grid and the
 * viewer. A no-op outside a shared transition layout (previews, tests).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mediaSharedElement(id: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@mediaSharedElement.sharedElement(
            rememberSharedContentState(key = mediaSharedKey(id)),
            animatedVisibilityScope = visibility
        )
    }
}
