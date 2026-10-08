package com.qtekfun.ultimategallery.data.edit

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.render.export.ExifCopier
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EditSaverTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val editor = ImageEditor(context, ExportImageLoader(context), ExifCopier(context), Dispatchers.Unconfined)
    private val store = FakeEditStore(context)
    private val broker = ConsentBroker()
    private val saver = MediaEditSaver(editor, store, broker, Dispatchers.Unconfined)

    private fun jpegItem(name: String = "IMG_1.jpg") = mediaItem(writeJpeg(quadrants(64, 32), File(folder.root, name)), "image/jpeg", 64, 32, name)

    private fun pngItem() = mediaItem(writePng(quadrants(40, 20), File(folder.root, "shot.png")), "image/png", 40, 20, "shot.png")

    /** Runs [block] while a fake activity answers every system dialog with [allow]. */
    private fun <T> withConsent(allow: Boolean, block: suspend () -> T): T = runBlocking {
        val activity = launch(Dispatchers.Default) { broker.requests.collect { broker.onResult(allow) } }
        try {
            block()
        } finally {
            activity.cancel()
        }
    }

    private fun orientation(bytes: ByteArray): Int {
        val file = folder.newFile().apply { writeBytes(bytes) }
        return ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

    @Test
    fun copyOfAPureJpegRotationIsLosslessAndNamedEdited() {
        val item = jpegItem()
        val original = File(requireNotNull(item.uri.path)).readBytes()
        val result = runBlocking { saver.save(item, CropRotateSpec(quarterTurns = 1), SaveBehavior.COPY) }
        assertTrue(result is SaveResult.Saved && !result.overwritten)
        val copy = store.inserted.single()
        assertEquals("IMG_1_edited.jpg", copy.displayName)
        assertEquals("image/jpeg", copy.mimeType)
        assertEquals("DCIM/Camera/", copy.relativePath)
        assertEquals(item.dateMs, copy.dateTakenMs)
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, orientation(copy.bytes))
        assertEquals(0, store.writeRequests)
        // The original is untouched.
        assertTrue(original.contentEquals(File(requireNotNull(item.uri.path)).readBytes()))
    }

    @Test
    fun copyWithACropReEncodesInTheSourceFormat() {
        val item = pngItem()
        val spec = CropRotateSpec(crop = CropRect(0.5f, 0f, 1f, 1f))
        val result = runBlocking { saver.save(item, spec, SaveBehavior.COPY) }
        assertTrue(result is SaveResult.Saved)
        val copy = store.inserted.single()
        assertEquals("shot_edited.png", copy.displayName)
        assertEquals("image/png", copy.mimeType)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(copy.bytes, 0, copy.bytes.size))
        assertEquals(20, bitmap.width)
        assertEquals(20, bitmap.height)
    }

    @Test
    fun askBehaviorIsTreatedAsACopyByTheSaver() {
        val result = runBlocking { saver.save(pngItem(), CropRotateSpec(quarterTurns = 1), SaveBehavior.ASK) }
        assertTrue(result is SaveResult.Saved && !result.overwritten)
        assertEquals(1, store.inserted.size)
    }

    @Test
    fun overwriteAsksTheSystemFirstAndStopsWhenDenied() {
        val item = jpegItem()
        val result = withConsent(allow = false) { saver.save(item, CropRotateSpec(quarterTurns = 1), SaveBehavior.OVERWRITE) }
        assertEquals(SaveResult.Denied, result)
        assertEquals(1, store.writeRequests)
        assertEquals(0, store.exifUpdates)
        assertTrue(store.overwritten.isEmpty())
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun losslessOverwriteOnlyChangesTheOrientationFlagInPlace() {
        val item = jpegItem()
        val result = withConsent(allow = true) { saver.save(item, CropRotateSpec(quarterTurns = 1), SaveBehavior.OVERWRITE) }
        assertEquals(SaveResult.Saved(item.uri, overwritten = true), result)
        assertEquals(1, store.exifUpdates)
        assertTrue(store.overwritten.isEmpty())
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface(File(requireNotNull(item.uri.path))).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
    }

    @Test
    fun overwriteWithACropWritesTheEncodedPhoto() {
        val item = pngItem()
        val spec = CropRotateSpec(quarterTurns = 1)
        val result = withConsent(allow = true) { saver.save(item, spec, SaveBehavior.OVERWRITE) }
        assertTrue(result is SaveResult.Saved && result.overwritten)
        val bytes = requireNotNull(store.overwritten[item.uri])
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertEquals(20, bitmap.width)
        assertEquals(40, bitmap.height)
        assertEquals(0, store.exifUpdates)
    }

    @Test
    fun overwriteOfAFormatThatCannotBeRewrittenFallsBackToACopy() {
        val file = writeJpeg(quadrants(64, 32), File(folder.root, "photo.heic"))
        val item = mediaItem(file, "image/heic", 64, 32)
        val result = withConsent(allow = true) { saver.save(item, CropRotateSpec(quarterTurns = 1), SaveBehavior.OVERWRITE) }
        assertTrue(result is SaveResult.Saved && !result.overwritten)
        assertEquals("photo_edited.jpg", store.inserted.single().displayName)
        assertEquals(0, store.writeRequests)
    }

    @Test
    fun copyNameDoesNotRepeatTheSuffix() {
        assertEquals("IMG_1_edited.jpg", EditNaming.copyName("IMG_1.jpeg", "jpg"))
        assertEquals("IMG_1_edited.jpg", EditNaming.copyName("IMG_1_edited.jpg", "jpg"))
        assertEquals("noext_edited.png", EditNaming.copyName("noext", "png"))
    }
}
