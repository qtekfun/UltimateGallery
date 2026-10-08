package com.qtekfun.ultimategallery.data.edit

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.di.IoDispatcher
import com.qtekfun.ultimategallery.domain.MediaItem
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Outcome of saving an edit. */
sealed interface SaveResult {
    /** Saved; [uri] is the new copy, or the original when it was overwritten. */
    data class Saved(val uri: Uri, val overwritten: Boolean) : SaveResult

    /** The user refused the system dialog that allows modifying the original. */
    data object Denied : SaveResult

    data class Failed(val cause: Throwable) : SaveResult
}

/** Writes the result of a crop and rotate edit according to a save behavior (R41). */
interface EditSaver {
    /**
     * Saves the edit of [item]. [SaveBehavior.COPY] adds a new photo next to the original,
     * [SaveBehavior.OVERWRITE] asks the system for write access and replaces the original. [SaveBehavior.ASK]
     * is a question for the UI and is treated as a copy here.
     */
    suspend fun save(item: MediaItem, spec: CropRotateSpec, behavior: SaveBehavior): SaveResult
}

class MediaEditSaver @Inject constructor(
    private val editor: ImageEditor,
    private val store: EditStore,
    private val consent: ConsentBroker,
    @IoDispatcher private val io: CoroutineDispatcher
) : EditSaver {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun save(item: MediaItem, spec: CropRotateSpec, behavior: SaveBehavior): SaveResult {
        val overwrite = behavior == SaveBehavior.OVERWRITE && ImageEditor.keepsFormat(item.mimeType)
        return try {
            if (overwrite) overwriteOriginal(item, spec) else saveCopy(item, spec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveResult.Failed(e)
        }
    }

    private suspend fun saveCopy(item: MediaItem, spec: CropRotateSpec): SaveResult = withContext(io) {
        val lossless = ImageEditor.isLossless(item.mimeType, spec)
        val file: File
        val mimeType: String
        val extension: String
        if (lossless) {
            file = copyOriginal(item)
            mimeType = item.mimeType
            extension = item.displayName.substringAfterLast('.', ImageEditor.formatFor(item.mimeType).extension)
            LosslessOrientation.apply(ExifInterface(file), spec)
        } else {
            val edited = editor.render(item.uri, item.mimeType, spec)
            file = edited.file
            mimeType = edited.format.mimeType
            extension = edited.format.extension
        }
        try {
            val uri = store.insertCopy(file, EditNaming.copyName(item.displayName, extension), mimeType, item.relativePath, item.dateMs)
            SaveResult.Saved(uri, overwritten = false)
        } finally {
            file.delete()
        }
    }

    private suspend fun overwriteOriginal(item: MediaItem, spec: CropRotateSpec): SaveResult {
        val allowed = consent.request(store.createWriteRequest(item.uri))
        if (!allowed) return SaveResult.Denied
        return withContext(io) {
            if (ImageEditor.isLossless(item.mimeType, spec)) {
                store.updateExifInPlace(item.uri) { LosslessOrientation.apply(it, spec) }
            } else {
                val edited = editor.render(item.uri, item.mimeType, spec)
                try {
                    store.overwrite(item.uri, edited.file)
                } finally {
                    edited.file.delete()
                }
            }
            SaveResult.Saved(item.uri, overwritten = true)
        }
    }

    private fun copyOriginal(item: MediaItem): File {
        val file = editor.newTempFile(ImageEditor.formatFor(item.mimeType).extension)
        try {
            val input = store.openOriginal(item.uri) ?: throw IOException("Could not read the photo")
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        return file
    }
}

/** Names of saved edits. */
object EditNaming {
    private const val SUFFIX = "_edited"

    /** `IMG_1.jpg` becomes `IMG_1_edited.jpg`; the extension is [extension]; an existing suffix is not repeated. */
    fun copyName(displayName: String, extension: String): String {
        val base = displayName.substringBeforeLast('.', displayName).ifBlank { "image" }
        val stem = if (base.endsWith(SUFFIX)) base else base + SUFFIX
        return "$stem.$extension"
    }
}
