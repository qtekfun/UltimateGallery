package com.qtekfun.ultimategallery.data.video

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.video.VideoRotateResult
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A [VideoStore] on plain files; "uris" are file uris and copies are files in the same folder. */
private class FakeVideoStore(private val context: Context, private val folder: File) : VideoStore {
    var writeRequests = 0
    var refreshed = mutableListOf<Uri>()
    val pending = mutableMapOf<Uri, Pair<String, File>>()
    val published = mutableListOf<Uri>()
    val deleted = mutableListOf<Uri>()
    var failCopy = false

    private fun file(uri: Uri) = File(requireNotNull(uri.path))

    override fun <T> withRead(uri: Uri, block: (RandomAccess) -> T): T = RandomAccessFile(file(uri), "r").use { block(FileChannelRandomAccess(it)) }

    override fun <T> withReadWrite(uri: Uri, block: (RandomAccess) -> T): T = RandomAccessFile(file(uri), "rw").use { block(FileChannelRandomAccess(it)) }

    override fun createWriteRequest(uri: Uri): IntentSender {
        writeRequests++
        return PendingIntent.getBroadcast(context, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE).intentSender
    }

    override fun refresh(uri: Uri) {
        refreshed += uri
    }

    override fun createPending(displayName: String, mimeType: String, relativePath: String?, dateTakenMs: Long): Uri {
        val target = File(folder, displayName)
        target.writeBytes(ByteArray(0))
        val uri = Uri.fromFile(target)
        pending[uri] = displayName to target
        return uri
    }

    override fun copyBytes(from: Uri, to: Uri) {
        if (failCopy) throw java.io.IOException("disk full")
        file(from).copyTo(file(to), overwrite = true)
    }

    override fun publish(uri: Uri) {
        published += uri
    }

    override fun delete(uri: Uri) {
        deleted += uri
        file(uri).delete()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaVideoRotationTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val broker = ConsentBroker()
    private lateinit var store: FakeVideoStore
    private lateinit var rotation: MediaVideoRotation

    private fun setUp() {
        store = FakeVideoStore(context, folder.root)
        rotation = MediaVideoRotation(store, broker, Dispatchers.Unconfined)
    }

    private fun videoItem(bytes: ByteArray, name: String = "VID_1.mp4"): MediaItem {
        val file = File(folder.root, name).apply { writeBytes(bytes) }
        return MediaItem(
            id = 9,
            uri = Uri.fromFile(file),
            displayName = name,
            mimeType = "video/mp4",
            isVideo = true,
            dateMs = 1_700_000_000_000L,
            width = 1920,
            height = 1080,
            sizeBytes = file.length(),
            durationMs = 1000,
            bucketId = 1,
            relativePath = "DCIM/Camera/"
        )
    }

    private fun <T> withConsent(allow: Boolean, block: suspend () -> T): T = runBlocking {
        val activity = launch(Dispatchers.Default) { broker.requests.collect { broker.onResult(allow) } }
        try {
            block()
        } finally {
            activity.cancel()
        }
    }

    private fun bytesOf(item: MediaItem) = File(requireNotNull(item.uri.path)).readBytes()

    private val sample get() = Mp4Builder.mp4(Mp4Builder.trak("vide", Mp4Builder.rotated90), Mp4Builder.trak("soun", Mp4Builder.identity))

    @Test
    fun readsTheCurrentRotation() {
        setUp()
        assertEquals(90, runBlocking { rotation.currentRotation(videoItem(sample)) })
        assertNull(runBlocking { rotation.currentRotation(videoItem(ByteArray(64) { 5 }, "x.mkv")) })
    }

    @Test
    fun overwritePatchesOnlyTheMatrixBytes() {
        setUp()
        val original = sample
        val item = videoItem(original)
        val result = withConsent(true) { rotation.rotate(item, 1, SaveBehavior.OVERWRITE) }
        assertEquals(VideoRotateResult.Rotated(item.uri, overwritten = true), result)
        val after = bytesOf(item)
        assertEquals(original.size, after.size)
        val changed = original.indices.filter { original[it] != after[it] }
        assertTrue(changed.isNotEmpty() && changed.last() - changed.first() < 36)
        assertArrayEquals(Mp4Builder.rotated180, Mp4Builder.matrixOf(after))
        // Everything outside the matrix is identical.
        val start = changed.first()
        assertArrayEquals(original.copyOfRange(0, start), after.copyOfRange(0, start))
        assertArrayEquals(original.copyOfRange(start + 36, original.size), after.copyOfRange(start + 36, after.size))
        assertEquals(1, store.writeRequests)
        assertEquals(listOf(item.uri), store.refreshed)
    }

    @Test
    fun deniedConsentChangesNothing() {
        setUp()
        val original = sample
        val item = videoItem(original)
        val result = withConsent(false) { rotation.rotate(item, 1, SaveBehavior.OVERWRITE) }
        assertEquals(VideoRotateResult.Denied, result)
        assertArrayEquals(original, bytesOf(item))
        assertTrue(store.refreshed.isEmpty())
    }

    @Test
    fun copyLeavesTheOriginalUntouched() {
        setUp()
        val original = sample
        val item = videoItem(original)
        for (behavior in listOf(SaveBehavior.COPY, SaveBehavior.ASK)) {
            val result = runBlocking { rotation.rotate(item, 2, behavior) }
            assertTrue(result is VideoRotateResult.Rotated && !result.overwritten)
            result as VideoRotateResult.Rotated
            assertEquals("VID_1_rotated.mp4", File(requireNotNull(result.uri.path)).name)
            assertArrayEquals(Mp4Builder.rotated270, Mp4Builder.matrixOf(File(requireNotNull(result.uri.path)).readBytes()))
            assertTrue(result.uri in store.published)
        }
        assertArrayEquals(original, bytesOf(item))
        assertEquals(0, store.writeRequests)
    }

    @Test
    fun failedCopyDeletesThePendingRow() {
        setUp()
        val item = videoItem(sample)
        store.failCopy = true
        val result = runBlocking { rotation.rotate(item, 1, SaveBehavior.COPY) }
        assertTrue(result is VideoRotateResult.Failed)
        assertEquals(1, store.deleted.size)
        assertTrue(store.published.isEmpty())
        assertFalse(File(requireNotNull(store.deleted.single().path)).exists())
    }

    @Test
    fun garbageIsUnsupportedAndNothingIsAskedOrCreated() {
        setUp()
        val item = videoItem(ByteArray(3000) { (it * 13).toByte() }, "clip.mkv")
        for (behavior in listOf(SaveBehavior.OVERWRITE, SaveBehavior.COPY)) {
            assertEquals(VideoRotateResult.Unsupported, withConsent(true) { rotation.rotate(item, 1, behavior) })
        }
        assertEquals(0, store.writeRequests)
        assertTrue(store.pending.isEmpty())
    }

    @Test
    fun copyNameKeepsTheExtension() {
        assertEquals("a_rotated.mov", MediaVideoRotation.copyName("a.mov"))
        assertEquals("a.b_rotated.mp4", MediaVideoRotation.copyName("a.b.mp4"))
        assertEquals("noext_rotated", MediaVideoRotation.copyName("noext"))
    }

    @Test
    fun videoRelativePathKeepsMediaFolders() {
        assertEquals("DCIM/Camera/", MediaStoreVideoStore.videoRelativePath("DCIM/Camera/"))
        assertEquals("Movies/", MediaStoreVideoStore.videoRelativePath(null))
        assertEquals("Movies/", MediaStoreVideoStore.videoRelativePath("Download/x/"))
        assertEquals("Movies/a/", MediaStoreVideoStore.videoRelativePath("Movies/../a/"))
    }
}
