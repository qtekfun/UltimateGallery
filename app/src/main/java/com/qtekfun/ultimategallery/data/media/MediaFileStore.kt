package com.qtekfun.ultimategallery.data.media

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** The platform calls behind [FileOperations], so tests can replace them with a fake. All calls block. */
@Suppress("TooManyFunctions")
interface MediaFileStore {
    fun trashRequest(uris: List<Uri>, trashed: Boolean): IntentSender

    fun writeRequest(uris: List<Uri>): IntentSender

    fun setDisplayName(uri: Uri, name: String): Boolean

    fun setRelativePath(uri: Uri, relativePath: String): Boolean

    /** Creates a hidden (pending) row and returns its uri, or null when the provider refuses. */
    fun insertPending(isVideo: Boolean, displayName: String, mimeType: String, dateTakenMs: Long, relativePath: String): Uri?

    fun openInput(uri: Uri): InputStream?

    fun openOutput(uri: Uri): OutputStream?

    /** Makes a pending row visible. */
    fun publish(uri: Uri)

    fun delete(uri: Uri)

    /** A new empty temp file in the app cache `shared/` folder. */
    fun tempFile(extension: String): File

    /** A content uri other apps can read for a file made with [tempFile]. */
    fun shareUri(file: File): Uri
}

/** [MediaFileStore] on the real content resolver. */
class ResolverFileStore(private val context: Context) : MediaFileStore {
    private val resolver get() = context.contentResolver

    override fun trashRequest(uris: List<Uri>, trashed: Boolean): IntentSender = MediaStore.createTrashRequest(resolver, uris, trashed).intentSender

    override fun writeRequest(uris: List<Uri>): IntentSender = MediaStore.createWriteRequest(resolver, uris).intentSender

    override fun setDisplayName(uri: Uri, name: String): Boolean =
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null) > 0

    override fun setRelativePath(uri: Uri, relativePath: String): Boolean =
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }, null, null) > 0

    override fun insertPending(isVideo: Boolean, displayName: String, mimeType: String, dateTakenMs: Long, relativePath: String): Uri? {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection, values)
    }

    override fun openInput(uri: Uri): InputStream? = resolver.openInputStream(uri)

    override fun openOutput(uri: Uri): OutputStream? = resolver.openOutputStream(uri)

    override fun publish(uri: Uri) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    override fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    override fun tempFile(extension: String): File {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        return File.createTempFile("share_", "." + extension, dir)
    }

    override fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    companion object {
        /** Sub-folder of the cache directory exposed through the FileProvider. */
        const val SHARED_DIR = "shared"
    }
}
