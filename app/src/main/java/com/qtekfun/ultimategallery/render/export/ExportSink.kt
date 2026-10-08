package com.qtekfun.ultimategallery.render.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Publishes a finished export file into the gallery. */
interface ExportSink {
    /**
     * Copies [file] to [relativePath] under the name [displayName] and returns the new uri together
     * with the name actually used, which can differ when the name was already taken.
     */
    suspend fun publish(file: File, displayName: String, mimeType: String, relativePath: String): Pair<Uri, String>
}

/** Writes exports into `MediaStore.Images` on the primary volume, hidden until fully written. */
class MediaStoreExportSink @Inject constructor(@ApplicationContext private val context: Context) : ExportSink {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun publish(file: File, displayName: String, mimeType: String, relativePath: String): Pair<Uri, String> {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, normalizeRelativePath(relativePath))
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create the output file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the output file")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val finalName = resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            return uri to (finalName ?: displayName)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    companion object {
        /**
         * MediaStore only accepts images under `Pictures/` or `DCIM/`. Returns the path without a leading
         * slash and with a trailing one; anything else is placed under `Pictures/`.
         */
        fun normalizeRelativePath(path: String): String {
            val clean = path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }.joinToString("/")
            val allowed = clean == "Pictures" || clean == "DCIM" || clean.startsWith("Pictures/") || clean.startsWith("DCIM/")
            val full = when {
                allowed -> clean
                clean.isEmpty() -> "Pictures"
                else -> "Pictures/$clean"
            }
            return "$full/"
        }
    }
}
