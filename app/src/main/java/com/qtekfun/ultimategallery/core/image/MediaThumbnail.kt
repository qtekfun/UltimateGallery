package com.qtekfun.ultimategallery.core.image

import android.content.Context
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension

/**
 * Model for a grid thumbnail. It is loaded with `ContentResolver.loadThumbnail`, which the system
 * caches on disk, instead of decoding the full photo for every cell.
 */
data class MediaThumbnail(val uri: Uri)

class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    override fun key(data: MediaThumbnail, options: Options): String = "thumb:${data.uri}"
}

class MediaThumbnailFetcher(private val context: Context, private val data: MediaThumbnail, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: DEFAULT_PX
        val h = (options.size.height as? Dimension.Pixels)?.px ?: DEFAULT_PX
        val bitmap = context.contentResolver.loadThumbnail(
            data.uri,
            Size(w.coerceIn(MIN_PX, MAX_PX), h.coerceIn(MIN_PX, MAX_PX)),
            null
        )
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher = MediaThumbnailFetcher(context, data, options)
    }

    private companion object {
        const val DEFAULT_PX = 256
        const val MIN_PX = 64
        const val MAX_PX = 1024
    }
}
