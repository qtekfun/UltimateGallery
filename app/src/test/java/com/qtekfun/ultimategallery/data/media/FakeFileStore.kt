package com.qtekfun.ultimategallery.data.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.domain.MediaItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** One fake MediaStore row. */
class FakeFile(var name: String, var path: String, var bytes: ByteArray, var pending: Boolean = false)

/** In-memory [MediaFileStore] for tests. Rows are keyed by the uri's last path segment. */
class FakeFileStore(private val tempDir: File) : MediaFileStore {
    val files = LinkedHashMap<Long, FakeFile>()
    var nextId = 100L
    var failOpenFor: Set<Long> = emptySet()
    var failRenameFor: Set<Long> = emptySet()
    var lastTrashed: Boolean? = null
    var trashRequests = 0
    var writeRequests = 0

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun sender(): IntentSender = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender

    private fun idOf(uri: Uri) = uri.lastPathSegment!!.toLong()

    fun uriOf(id: Long): Uri = Uri.parse("content://fake/media/$id")

    fun add(id: Long, name: String, path: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): MediaItem {
        files[id] = FakeFile(name, path, bytes)
        return item(id, name, path)
    }

    fun item(id: Long, name: String, path: String, mime: String = "image/jpeg", isVideo: Boolean = false) = MediaItem(
        id = id, uri = uriOf(id), displayName = name, mimeType = mime, isVideo = isVideo, dateMs = 5_000L, width = 10, height = 10,
        sizeBytes = 3, durationMs = 0, bucketId = 1, relativePath = path
    )

    override fun trashRequest(uris: List<Uri>, trashed: Boolean): IntentSender {
        trashRequests++
        lastTrashed = trashed
        return sender()
    }

    override fun writeRequest(uris: List<Uri>): IntentSender {
        writeRequests++
        return sender()
    }

    override fun setDisplayName(uri: Uri, name: String): Boolean {
        val id = idOf(uri)
        if (id in failRenameFor) return false
        val file = files[id] ?: return false
        file.name = name
        return true
    }

    override fun setRelativePath(uri: Uri, relativePath: String): Boolean {
        val id = idOf(uri)
        if (id in failRenameFor) return false
        val file = files[id] ?: return false
        file.path = relativePath
        return true
    }

    override fun insertPending(isVideo: Boolean, displayName: String, mimeType: String, dateTakenMs: Long, relativePath: String): Uri {
        val id = nextId++
        files[id] = FakeFile(displayName, relativePath, ByteArray(0), pending = true)
        return uriOf(id)
    }

    override fun openInput(uri: Uri): InputStream? {
        val id = idOf(uri)
        if (id in failOpenFor) return null
        return files[id]?.let { ByteArrayInputStream(it.bytes) }
    }

    override fun openOutput(uri: Uri): OutputStream {
        val file = files.getValue(idOf(uri))
        return object : ByteArrayOutputStream() {
            override fun close() {
                file.bytes = toByteArray()
            }
        }
    }

    override fun publish(uri: Uri) {
        files[idOf(uri)]?.pending = false
    }

    override fun delete(uri: Uri) {
        files.remove(idOf(uri))
    }

    override fun tempFile(extension: String): File = File.createTempFile("t_", ".$extension", tempDir)

    override fun shareUri(file: File): Uri = Uri.parse("content://fake.files/shared/${file.name}")
}

/** Consent that answers [allow] and counts requests. */
class FakeConsent(var allow: Boolean = true) : ConsentRequester {
    var requests = 0

    override suspend fun request(intentSender: IntentSender): Boolean {
        requests++
        return allow
    }
}
