package com.qtekfun.ultimategallery.data.edit

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.qtekfun.ultimategallery.render.export.MediaStoreExportSink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** The MediaStore operations an edit needs, behind an interface so the save logic can be tested without a provider. */
interface EditStore {
    /** Opens the unredacted bytes of the photo at [uri], or null when that is impossible. */
    fun openOriginal(uri: Uri): InputStream?

    /**
     * Publishes [file] as a new image under [relativePath] (the folder of the original, or null for
     * `Pictures/`) named [displayName], keeping [dateTakenMs]. The name is de-duplicated by the system.
     */
    fun insertCopy(file: File, displayName: String, mimeType: String, relativePath: String?, dateTakenMs: Long): Uri

    /** Replaces the content of the photo at [uri] (which must be writable) with [file]. */
    fun overwrite(uri: Uri, file: File)

    /** Lets [block] edit and save the EXIF block of the photo at [uri] in place, without touching its pixels. */
    fun updateExifInPlace(uri: Uri, block: (ExifInterface) -> Unit)

    /** The system dialog asking the user to allow this app to modify [uri]. */
    fun createWriteRequest(uri: Uri): IntentSender
}

/** [EditStore] backed by `MediaStore.Images` on the primary volume. */
class MediaStoreEditStore @Inject constructor(@ApplicationContext private val context: Context) : EditStore {
    private val resolver get() = context.contentResolver

    override fun openOriginal(uri: Uri): InputStream? {
        val candidates = buildList {
            if (uri.scheme == "content") {
                try {
                    add(MediaStore.setRequireOriginal(uri))
                } catch (_: UnsupportedOperationException) {
                    // Not a MediaStore uri: use it as it is.
                }
            }
            add(uri)
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            try {
                resolver.openInputStream(candidate)
            } catch (_: SecurityException) {
                null
            } catch (_: IOException) {
                null
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun insertCopy(file: File, displayName: String, mimeType: String, relativePath: String?, dateTakenMs: Long): Uri {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, MediaStoreExportSink.normalizeRelativePath(relativePath.orEmpty()))
            put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMs)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create the output file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the output file")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    override fun overwrite(uri: Uri, file: File) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the photo for writing")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }

    override fun updateExifInPlace(uri: Uri, block: (ExifInterface) -> Unit) {
        val descriptor = resolver.openFileDescriptor(uri, "rw") ?: throw IOException("Could not open the photo for writing")
        descriptor.use { block(ExifInterface(it.fileDescriptor)) }
    }

    override fun createWriteRequest(uri: Uri): IntentSender = MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
}
