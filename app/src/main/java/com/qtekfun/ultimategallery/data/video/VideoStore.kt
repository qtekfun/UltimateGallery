package com.qtekfun.ultimategallery.data.video

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

/** The MediaStore operations video rotation needs, behind an interface so the logic can be tested on plain files. */
interface VideoStore {
    /** Runs [block] with read-only access to the bytes of [uri]. */
    fun <T> withRead(uri: Uri, block: (RandomAccess) -> T): T

    /** Runs [block] with read and write access to [uri], never truncating it. The caller must hold write consent. */
    fun <T> withReadWrite(uri: Uri, block: (RandomAccess) -> T): T

    /** The system dialog asking the user to allow this app to modify [uri]. */
    fun createWriteRequest(uri: Uri): IntentSender

    /** Asks the system to regenerate thumbnails and cached metadata of [uri] after its bytes changed. */
    fun refresh(uri: Uri)

    /** Creates a hidden (pending) video row under [relativePath] (null for `Movies/`) and returns its uri. */
    fun createPending(displayName: String, mimeType: String, relativePath: String?, dateTakenMs: Long): Uri

    /** Copies all bytes of [from] into [to]. */
    fun copyBytes(from: Uri, to: Uri)

    /** Makes a pending row visible. */
    fun publish(uri: Uri)

    /** Removes a row, ignoring failures. */
    fun delete(uri: Uri)
}

/** [VideoStore] backed by `MediaStore.Video` on the primary volume. */
class MediaStoreVideoStore @Inject constructor(@ApplicationContext private val context: Context) : VideoStore {
    private val resolver get() = context.contentResolver

    override fun <T> withRead(uri: Uri, block: (RandomAccess) -> T): T {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw IOException("Could not open the video")
        return descriptor.use { FileInputStream(it.fileDescriptor).use { stream -> block(FileChannelRandomAccess(stream.channel)) } }
    }

    override fun <T> withReadWrite(uri: Uri, block: (RandomAccess) -> T): T {
        // "rw", never "rwt": the file must not be truncated.
        val descriptor = resolver.openFileDescriptor(uri, "rw") ?: throw IOException("Could not open the video for writing")
        return descriptor.use {
            FileInputStream(it.fileDescriptor).use { input ->
                FileOutputStream(it.fileDescriptor).use { output -> block(FileChannelRandomAccess(input.channel, output.channel)) }
            }
        }
    }

    override fun createWriteRequest(uri: Uri): IntentSender = MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender

    override fun refresh(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000) }
        runCatching { resolver.update(uri, values, null, null) }
    }

    override fun createPending(displayName: String, mimeType: String, relativePath: String?, dateTakenMs: Long): Uri {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, videoRelativePath(relativePath))
            put(MediaStore.Video.Media.DATE_TAKEN, dateTakenMs)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return resolver.insert(collection, values) ?: throw IOException("Could not create the output file")
    }

    override fun copyBytes(from: Uri, to: Uri) {
        val input = resolver.openInputStream(from) ?: throw IOException("Could not read the video")
        val output = resolver.openOutputStream(to) ?: throw IOException("Could not open the output file")
        input.use { source -> output.use { sink -> source.copyTo(sink) } }
    }

    override fun publish(uri: Uri) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    }

    override fun delete(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    companion object {
        /** The folder of the original when it is a normal media folder, otherwise `Movies/`; always with a trailing slash. */
        fun videoRelativePath(path: String?): String {
            val clean = path.orEmpty().replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }
            val root = clean.firstOrNull()
            return if (root == "Movies" || root == "DCIM" || root == "Pictures") clean.joinToString("/") + "/" else "Movies/"
        }
    }
}
