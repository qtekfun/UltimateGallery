package com.qtekfun.ultimategallery.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Decoded logo bitmaps, loaded from app-private storage and cached. Logos are decoded at most
 * [MAX_EDGE] pixels on the long edge, which is plenty for a watermark.
 */
@Singleton
class WatermarkBitmapStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /** Returns the cached bitmap, loading it from disk on first use; null when it cannot be decoded. */
    fun get(uri: Uri): Bitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        val bitmap = decode(uri) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun evict(uri: Uri) {
        cache.remove(uri.toString())
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val source = if (uri.scheme == "file") {
            ImageDecoder.createSource(File(requireNotNull(uri.path)))
        } else {
            ImageDecoder.createSource(context.contentResolver, uri)
        }
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longEdge
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    }.getOrNull()

    private companion object {
        const val MAX_EDGE = 2048
        const val CACHE_ENTRIES = 8
    }
}
