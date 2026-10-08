package com.qtekfun.ultimategallery.core.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qtekfun.ultimategallery.core.image.MediaThumbnail

/** A cropped grid thumbnail of [uri], served from the system thumbnail cache. */
@Composable
fun MediaThumb(uri: Uri, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context).data(MediaThumbnail(uri)).crossfade(true).build()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}
