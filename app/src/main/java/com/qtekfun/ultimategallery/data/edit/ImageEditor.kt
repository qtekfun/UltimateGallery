package com.qtekfun.ultimategallery.data.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import com.qtekfun.ultimategallery.feature.edit.CropGeometry
import com.qtekfun.ultimategallery.render.export.ExifCopier
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The encoding an edited photo is written in. */
data class OutputFormat(val mimeType: String, val extension: String, val compressFormat: Bitmap.CompressFormat, val quality: Int)

/** An edited photo written to a temporary file, not yet published. */
class EditedImage(val file: File, val format: OutputFormat, val width: Int, val height: Int)

/**
 * Applies a [CropRotateSpec] to the full resolution photo. The photo is decoded with its EXIF
 * orientation, turned, flipped, straightened and cropped on a canvas, and encoded in the source
 * format. Camera metadata is carried over with the orientation reset.
 */
@Singleton
class ImageEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: ExportImageLoader,
    private val exifCopier: ExifCopier,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** Renders [spec] on the photo at [source] and writes the result to a temporary file. */
    suspend fun render(source: Uri, mimeType: String, spec: CropRotateSpec): EditedImage = withContext(io) {
        val format = formatFor(mimeType)
        val src = loader.load(source, null).bitmap
        val out = try {
            draw(src, spec, format)
        } finally {
            src.recycle()
        }
        try {
            val file = newTempFile(format.extension)
            file.outputStream().use { stream ->
                if (!out.compress(format.compressFormat, format.quality, stream)) throw IOException("Could not encode the edited photo")
            }
            exifCopier.apply(file, source, ExifMode.KEEP, out.width, out.height)
            EditedImage(file, format, out.width, out.height)
        } finally {
            out.recycle()
        }
    }

    /** A new empty file in the cache folder for edit results; the caller deletes it. */
    fun newTempFile(extension: String): File {
        val dir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File.createTempFile("edit-", ".$extension", dir)
    }

    private fun draw(src: Bitmap, spec: CropRotateSpec, format: OutputFormat): Bitmap {
        val (fullW, fullH) = CropGeometry.outputSize(src.width, src.height, spec)
        // A straightened edge is anti-aliased against nothing; stay one pixel inside it.
        val inset = if (spec.straightenDeg != 0f && fullW > MIN_INSET_SIDE && fullH > MIN_INSET_SIDE) 1 else 0
        val out = try {
            Bitmap.createBitmap(fullW - 2 * inset, fullH - 2 * inset, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw IOException("Not enough memory to edit this photo", e)
        }
        val m = CropGeometry.compositeAffine(src.width, src.height, spec)
        val matrix = Matrix().apply {
            setValues(floatArrayOf(m[0].toFloat(), m[2].toFloat(), m[4].toFloat(), m[1].toFloat(), m[3].toFloat(), m[5].toFloat(), 0f, 0f, 1f))
            postTranslate(-inset.toFloat(), -inset.toFloat())
        }
        val canvas = Canvas(out)
        if (format.compressFormat == Bitmap.CompressFormat.JPEG) canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    companion object {
        private const val TEMP_DIR = "edits"
        private const val QUALITY = 95
        private const val MIN_INSET_SIDE = 8
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
        const val MIME_WEBP = "image/webp"

        /** The encoding used to save an edit of a photo of type [mimeType]; anything unwritable becomes JPEG. */
        fun formatFor(mimeType: String): OutputFormat = when (mimeType.lowercase()) {
            MIME_PNG -> OutputFormat(MIME_PNG, "png", Bitmap.CompressFormat.PNG, QUALITY)
            MIME_WEBP -> OutputFormat(MIME_WEBP, "webp", Bitmap.CompressFormat.WEBP_LOSSY, QUALITY)
            else -> OutputFormat(MIME_JPEG, "jpg", Bitmap.CompressFormat.JPEG, QUALITY)
        }

        /** True when a photo of [mimeType] can be rewritten in its own format (needed to overwrite it). */
        fun keepsFormat(mimeType: String): Boolean = mimeType.lowercase() in setOf(MIME_JPEG, MIME_PNG, MIME_WEBP)

        /** True when [spec] can be applied to a photo of [mimeType] by changing only the EXIF orientation. */
        fun isLossless(mimeType: String, spec: CropRotateSpec): Boolean = mimeType.equals(MIME_JPEG, ignoreCase = true) && spec.isOrientationOnly
    }
}

/** Lossless rotation and flip of a JPEG: only the EXIF orientation flag changes. */
object LosslessOrientation {
    /**
     * Composes the turns and flips of [spec] with the orientation already stored in [exif] and saves it.
     * The pixel size tags are swapped for odd turns. Throws [IOException] when the file can't be written.
     */
    fun apply(exif: ExifInterface, spec: CropRotateSpec) {
        // A missing or undefined orientation reads as 0, which rotate and flip would leave alone.
        val stored = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (stored !in ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        }
        if (spec.turns != 0) exif.rotate(spec.turns * QUARTER)
        if (spec.flipH) exif.flipHorizontally()
        if (spec.flipV) exif.flipVertically()
        if (spec.turns % 2 != 0) swapDimensions(exif)
        exif.saveAttributes()
    }

    private fun swapDimensions(exif: ExifInterface) {
        val x = exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION)
        val y = exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION)
        if (x != null && y != null) {
            exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, y)
            exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, x)
        }
    }

    private const val QUARTER = 90
}
