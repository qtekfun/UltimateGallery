package com.qtekfun.ultimategallery.render.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** A decoded photo ready to be drawn on. */
class LoadedImage(
    /** Mutable, software bitmap with the EXIF orientation applied. */
    val bitmap: Bitmap,
    /** True when the photo exceeded the pixel budget and was decoded smaller to fit it. */
    val downscaled: Boolean,
    /** File name of the source when it could be determined. */
    val displayName: String?
)

/**
 * Decodes the photo of an export with a bounded memory use. The EXIF orientation is applied by
 * [ImageDecoder]; a photo is never enlarged.
 */
class ExportImageLoader(private val context: Context, private val pixelBudget: Long) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, DEFAULT_PIXEL_BUDGET)

    /**
     * Decodes [uri]. When [maxLongEdge] is set and smaller than the photo, the result has exactly that
     * long edge. A photo larger than the pixel budget is decoded at a power-of-two fraction of its size.
     */
    fun load(uri: Uri, maxLongEdge: Int?): LoadedImage {
        val name = displayNameOf(uri)
        val loaded = try {
            decode(uri, maxLongEdge, pixelBudget)
        } catch (_: OutOfMemoryError) {
            decode(uri, maxLongEdge, pixelBudget / 2)
        }
        return LoadedImage(loaded.bitmap, loaded.downscaled, name)
    }

    private class Decoded(val bitmap: Bitmap, val downscaled: Boolean)

    private fun decode(uri: Uri, maxLongEdge: Int?, budget: Long): Decoded {
        var downscaled = false
        val bitmap = ImageDecoder.decodeBitmap(sourceOf(uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val width = info.size.width
            val height = info.size.height
            val budgetSample = sampleForBudget(width, height, budget)
            downscaled = budgetSample > 1
            val sample = max(budgetSample, maxLongEdge?.let { sampleForEdge(max(width, height), it) } ?: 1)
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        return Decoded(fitLongEdge(bitmap, maxLongEdge), downscaled)
    }

    /** Resizes to exactly [maxLongEdge] when the bitmap is larger; the result is always mutable. */
    private fun fitLongEdge(bitmap: Bitmap, maxLongEdge: Int?): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (maxLongEdge == null || maxLongEdge <= 0 || longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toDouble() / longEdge
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        if (scaled.isMutable) return scaled
        return scaled.copy(Bitmap.Config.ARGB_8888, true).also { scaled.recycle() }
    }

    private fun sourceOf(uri: Uri): ImageDecoder.Source = if (uri.scheme == "file") {
        ImageDecoder.createSource(File(requireNotNull(uri.path)))
    } else {
        ImageDecoder.createSource(context.contentResolver, uri)
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") {
            uri.lastPathSegment
        } else {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }
    }.getOrNull()

    companion object {
        /** 64 megapixels: about 256 MB as ARGB_8888. */
        const val DEFAULT_PIXEL_BUDGET = 64_000_000L

        /** The smallest power of two that brings [width] x [height] within [budget] pixels. */
        fun sampleForBudget(width: Int, height: Int, budget: Long): Int {
            var sample = 1
            while (width.toLong() / sample * (height.toLong() / sample) > budget) sample *= 2
            return sample
        }

        /** The largest power of two that keeps [longEdge] at or above [target]. */
        fun sampleForEdge(longEdge: Int, target: Int): Int {
            var sample = 1
            while (target > 0 && longEdge / (sample * 2) >= target) sample *= 2
            return sample
        }
    }
}
