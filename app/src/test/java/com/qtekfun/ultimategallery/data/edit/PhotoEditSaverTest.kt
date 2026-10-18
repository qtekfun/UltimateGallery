package com.qtekfun.ultimategallery.data.edit

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.core.consent.ConsentBroker
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.domain.edit.Adjustments
import com.qtekfun.ultimategallery.domain.edit.BackgroundEdit
import com.qtekfun.ultimategallery.domain.edit.BackgroundFill
import com.qtekfun.ultimategallery.domain.edit.MaskOp
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.render.edit.PhotoEditRenderer
import com.qtekfun.ultimategallery.render.export.ExifCopier
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PhotoEditSaverTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val editor = ImageEditor(context, ExportImageLoader(context), ExifCopier(context), Dispatchers.Unconfined)
    private val images = PhotoEditImages(ExportImageLoader(context), PhotoEditRenderer { null }, ExifCopier(context), editor)
    private val store = FakeEditStore(context)
    private val broker = ConsentBroker()
    private val saver = MediaPhotoEditSaver(images, store, broker, Dispatchers.Unconfined)

    private val removeBackground = PhotoEdit(background = BackgroundEdit(listOf(MaskOp.Auto(0.3f)), feather = 0f))

    private fun jpegItem() = mediaItem(writeJpeg(quadrants(64, 32), File(folder.root, "IMG_1.jpg")), "image/jpeg", 64, 32, "IMG_1.jpg")

    private fun pngItem() = mediaItem(writePng(quadrants(40, 20), File(folder.root, "shot.png")), "image/png", 40, 20, "shot.png")

    private fun <T> withConsent(allow: Boolean, block: suspend () -> T): T = runBlocking {
        val activity = launch(Dispatchers.Default) { broker.requests.collect { broker.onResult(allow) } }
        try {
            block()
        } finally {
            activity.cancel()
        }
    }

    @Test
    fun outputFormatKeepsTheSourceUnlessTransparencyNeedsPng() {
        assertEquals("image/jpeg", PhotoEditImages.outputFormat("image/jpeg", transparent = false).mimeType)
        assertEquals("image/png", PhotoEditImages.outputFormat("image/jpeg", transparent = true).mimeType)
        assertEquals("image/webp", PhotoEditImages.outputFormat("image/webp", transparent = true).mimeType)
        assertEquals("image/jpeg", PhotoEditImages.outputFormat("image/heic", transparent = false).mimeType)
        assertTrue(PhotoEditImages.canOverwrite("image/png", transparent = true))
        assertFalse(PhotoEditImages.canOverwrite("image/jpeg", transparent = true))
        assertFalse(PhotoEditImages.canOverwrite("image/heic", transparent = false))
    }

    @Test
    fun copyIsRenderedAndNamedEdited() {
        val item = jpegItem()
        val result = runBlocking { saver.save(item, PhotoEdit(adjust = Adjustments(saturation = -1f)), SaveBehavior.COPY) }
        assertTrue(result is SaveResult.Saved && !result.overwritten)
        val copy = store.inserted.single()
        assertEquals("IMG_1_edited.jpg", copy.displayName)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(copy.bytes, 0, copy.bytes.size))
        val p = bitmap.getPixel(16, 8)
        assertTrue(kotlin.math.abs(Color.red(p) - Color.blue(p)) < 12)
    }

    @Test
    fun transparentJpegIsSavedAsAPngCopyEvenWhenOverwriteIsAsked() {
        val item = jpegItem()
        val original = File(requireNotNull(item.uri.path)).readBytes()
        val result = withConsent(true) { saver.save(item, removeBackground, SaveBehavior.OVERWRITE) }
        assertTrue(result is SaveResult.Saved && !result.overwritten)
        assertEquals(0, store.writeRequests)
        val copy = store.inserted.single()
        assertEquals("IMG_1_edited.png", copy.displayName)
        assertEquals("image/png", copy.mimeType)
        assertTrue(original.contentEquals(File(requireNotNull(item.uri.path)).readBytes()))
    }

    @Test
    fun solidBackgroundOnJpegCanOverwrite() {
        val item = jpegItem()
        val edit = removeBackground.copy(background = removeBackground.background.copy(fill = BackgroundFill.Solid(Color.WHITE)))
        val result = withConsent(true) { saver.save(item, edit, SaveBehavior.OVERWRITE) }
        assertTrue(result is SaveResult.Saved && result.overwritten)
        assertEquals(1, store.overwritten.size)
    }

    @Test
    fun pngOverwriteNeedsConsent() {
        val item = pngItem()
        val denied = withConsent(false) { saver.save(item, removeBackground, SaveBehavior.OVERWRITE) }
        assertEquals(SaveResult.Denied, denied)
        val saved = withConsent(true) { saver.save(item, removeBackground, SaveBehavior.OVERWRITE) }
        assertTrue(saved is SaveResult.Saved && saved.overwritten)
        val bytes = store.overwritten.values.single()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertTrue(bitmap.hasAlpha())
    }
}
