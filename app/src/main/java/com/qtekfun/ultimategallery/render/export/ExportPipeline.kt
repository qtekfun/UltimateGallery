package com.qtekfun.ultimategallery.render.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportItemResult
import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Turns one photo into one watermarked file: decode, resize, draw with the shared renderer, encode,
 * copy EXIF and publish. Originals are only read.
 */
class ExportPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: ExportImageLoader,
    private val renderer: WatermarkRenderer,
    private val exif: ExifCopier,
    private val sink: ExportSink,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /**
     * Exports [item] at position [index] of a batch of [total]. [onStage] receives 0.1 once decoded, 0.5
     * drawn, 0.8 encoded and 1.0 written. Failures become a result with an error; cancellation propagates.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun exportOne(item: MediaItem, profile: WatermarkProfile, index: Int, total: Int, onStage: (Float) -> Unit): ExportItemResult = withContext(io) {
        var bitmap: Bitmap? = null
        var temp: File? = null
        try {
            val settings = profile.export
            val loaded = loader.load(item.uri, settings.maxLongEdge)
            bitmap = loaded.bitmap
            onStage(STAGE_DECODED)
            currentCoroutineContext().ensureActive()

            if (settings.format != ExportFormat.PNG && bitmap.hasAlpha()) bitmap = flattenOnWhite(bitmap)
            renderer.draw(Canvas(bitmap), bitmap.width, bitmap.height, profile)
            onStage(STAGE_DRAWN)
            currentCoroutineContext().ensureActive()

            val file = File.createTempFile("export_", ".${settings.format.extension}", context.cacheDir)
            temp = file
            encode(bitmap, settings.format, settings.quality, file)
            exif.apply(file, item.uri, settings.exif, bitmap.width, bitmap.height)
            onStage(STAGE_ENCODED)
            currentCoroutineContext().ensureActive()

            val original = item.displayName.ifBlank { loaded.displayName.orEmpty() }
            val name = ExportNaming.fileName(settings.fileNamePattern, original, index, total, settings.format.extension)
            val (uri, finalName) = sink.publish(file, name, settings.format.mimeType, settings.destination)
            onStage(STAGE_WRITTEN)
            ExportItemResult(item.id, uri, finalName, null, loaded.downscaled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            failure(item, "Out of memory")
        } catch (e: Exception) {
            failure(item, e.message ?: e.javaClass.simpleName)
        } finally {
            temp?.delete()
            bitmap?.recycle()
        }
    }

    private fun failure(item: MediaItem, message: String) = ExportItemResult(item.id, null, null, message)

    /** Returns an opaque copy of [source] drawn on white, and recycles [source]. */
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        flat.eraseColor(Color.WHITE)
        Canvas(flat).drawBitmap(source, 0f, 0f, null)
        source.recycle()
        return flat
    }

    private fun encode(bitmap: Bitmap, format: ExportFormat, quality: Int, file: File) {
        val compressFormat = when (format) {
            ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(compressFormat, quality.coerceIn(1, 100), out)) throw IOException("Could not encode the image")
        }
    }

    private companion object {
        const val STAGE_DECODED = 0.1f
        const val STAGE_DRAWN = 0.5f
        const val STAGE_ENCODED = 0.8f
        const val STAGE_WRITTEN = 1f
    }
}
