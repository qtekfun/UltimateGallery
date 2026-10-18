package com.qtekfun.ultimategallery.data.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import com.qtekfun.ultimategallery.render.edit.PhotoEditRenderer
import com.qtekfun.ultimategallery.render.export.ExifCopier
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Renders a [PhotoEdit] on the full photo and writes it to a temporary file. */
class PhotoEditImages(
    private val loader: ExportImageLoader,
    private val renderer: PhotoEditRenderer,
    private val exifCopier: ExifCopier,
    private val editor: ImageEditor
) {
    @Inject
    constructor(@ApplicationContext context: Context, fonts: FontLibrary, exifCopier: ExifCopier, editor: ImageEditor) :
        this(ExportImageLoader(context, EDIT_PIXEL_BUDGET), PhotoEditRenderer(fonts::typeface), exifCopier, editor)

    /** Must be called off the main thread. */
    fun render(source: Uri, mimeType: String, edit: PhotoEdit): EditedImage {
        val format = outputFormat(mimeType, edit.hasTransparency)
        val src = loader.load(source, null).bitmap
        var out = try {
            renderer.render(src, edit)
        } finally {
            src.recycle()
        }
        if (format.compressFormat == Bitmap.CompressFormat.JPEG && !edit.background.isIdentity) out = flattened(out)
        try {
            val file = editor.newTempFile(format.extension)
            file.outputStream().use { stream ->
                if (!out.compress(format.compressFormat, format.quality, stream)) throw IOException("Could not encode the edited photo")
            }
            exifCopier.apply(file, source, ExifMode.KEEP, out.width, out.height)
            return EditedImage(file, format, out.width, out.height)
        } finally {
            out.recycle()
        }
    }

    /** JPEG has no alpha: anything translucent is laid on white. */
    private fun flattened(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, Paint())
        }
        src.recycle()
        return out
    }

    companion object {
        /** Photos above this many pixels are edited at a power-of-two fraction of their size. */
        const val EDIT_PIXEL_BUDGET = 16_000_000L

        /** The format of the edit of a photo of type [mimeType]: its own, or PNG when transparency must survive and it has none. */
        fun outputFormat(mimeType: String, transparent: Boolean): OutputFormat {
            val own = ImageEditor.formatFor(mimeType)
            return if (transparent && own.compressFormat == Bitmap.CompressFormat.JPEG) ImageEditor.formatFor(ImageEditor.MIME_PNG) else own
        }

        /** True when the edit can replace the original: the original is writable and keeps its format. */
        fun canOverwrite(mimeType: String, transparent: Boolean): Boolean =
            ImageEditor.keepsFormat(mimeType) && outputFormat(mimeType, transparent).mimeType.equals(mimeType, ignoreCase = true)
    }
}

/** Writes the result of the photo editor according to a save behavior (R41). */
interface PhotoEditSaver {
    /**
     * Saves [edit] of [item]. [SaveBehavior.OVERWRITE] replaces the original only when the result keeps its format (a JPEG
     * with a transparent background becomes a PNG copy); everything else is saved as a copy next to the original.
     */
    suspend fun save(item: MediaItem, edit: PhotoEdit, behavior: SaveBehavior): SaveResult
}

class MediaPhotoEditSaver @Inject constructor(
    private val images: PhotoEditImages,
    private val store: EditStore,
    private val consent: ConsentBroker,
    @IoDispatcher private val io: CoroutineDispatcher
) : PhotoEditSaver {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun save(item: MediaItem, edit: PhotoEdit, behavior: SaveBehavior): SaveResult {
        val overwrite = behavior == SaveBehavior.OVERWRITE && PhotoEditImages.canOverwrite(item.mimeType, edit.hasTransparency)
        return try {
            if (overwrite) overwriteOriginal(item, edit) else saveCopy(item, edit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveResult.Failed(e)
        } catch (e: OutOfMemoryError) {
            SaveResult.Failed(e)
        }
    }

    private suspend fun saveCopy(item: MediaItem, edit: PhotoEdit): SaveResult = withContext(io) {
        val edited = images.render(item.uri, item.mimeType, edit)
        try {
            val name = EditNaming.copyName(item.displayName, edited.format.extension)
            val uri = store.insertCopy(edited.file, name, edited.format.mimeType, item.relativePath, item.dateMs)
            SaveResult.Saved(uri, overwritten = false)
        } finally {
            edited.file.delete()
        }
    }

    private suspend fun overwriteOriginal(item: MediaItem, edit: PhotoEdit): SaveResult {
        if (!consent.request(store.createWriteRequest(item.uri))) return SaveResult.Denied
        return withContext(io) {
            val edited = images.render(item.uri, item.mimeType, edit)
            try {
                store.overwrite(item.uri, edited.file)
            } finally {
                edited.file.delete()
            }
            SaveResult.Saved(item.uri, overwritten = true)
        }
    }
}
