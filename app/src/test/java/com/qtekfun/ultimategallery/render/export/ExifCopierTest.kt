package com.qtekfun.ultimategallery.render.export

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ExifCopierTest {
    @get:Rule val folder = TemporaryFolder()
    private val copier = ExifCopier(ApplicationProvider.getApplicationContext())

    private fun original(): File {
        val file = TestImages.writeJpeg(TestImages.solid(80, 40, Color.GRAY), folder.newFile("original.jpg"))
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, "TestMake")
            setAttribute(ExifInterface.TAG_MODEL, "TestModel")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "400")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:05:06 07:08:09")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(41.39, 2.17)
            saveAttributes()
        }
        return file
    }

    private fun output(format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, name: String = "out.jpg") =
        TestImages.write(TestImages.solid(40, 80, Color.BLUE), folder.newFile(name), format)

    @Test
    fun keepCopiesCameraDataAndLocationButNormalisesOrientationAndSize() {
        val out = output()
        assertTrue(copier.apply(out, Uri.fromFile(original()), ExifMode.KEEP, 40, 80))
        val exif = ExifInterface(out)
        assertEquals("TestMake", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("TestModel", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("400", exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
        assertEquals("2024:05:06 07:08:09", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        val latLong = exif.latLong
        assertNotNull(latLong)
        assertEquals(41.39, latLong!![0], 0.001)
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        assertEquals("40", exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION))
        assertEquals("80", exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION))
    }

    @Test
    fun stripLocationKeepsEverythingButGps() {
        val out = output()
        assertTrue(copier.apply(out, Uri.fromFile(original()), ExifMode.STRIP_LOCATION, 40, 80))
        val exif = ExifInterface(out)
        assertEquals("TestMake", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.latLong)
        ExifCopier.GPS_TAGS.forEach { assertNull(it, exif.getAttribute(it)) }
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
    }

    @Test
    fun stripLocationRemovesGpsAlreadyPresentInTheOutput() {
        val out = output()
        ExifInterface(out).apply {
            setLatLong(1.0, 2.0)
            saveAttributes()
        }
        assertTrue(copier.apply(out, Uri.fromFile(original()), ExifMode.STRIP_LOCATION, 40, 80))
        assertNull(ExifInterface(out).latLong)
    }

    @Test
    fun stripAllWritesNoMetadata() {
        val out = output()
        assertTrue(copier.apply(out, Uri.fromFile(original()), ExifMode.STRIP_ALL, 40, 80))
        val exif = ExifInterface(out)
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.latLong)
    }

    @Test
    fun worksOnPngAndWebp() {
        val source = Uri.fromFile(original())
        val png = output(Bitmap.CompressFormat.PNG, "out.png")
        val webp = output(Bitmap.CompressFormat.WEBP_LOSSY, "out.webp")
        assertTrue(copier.apply(png, source, ExifMode.STRIP_LOCATION, 40, 80))
        assertTrue(copier.apply(webp, source, ExifMode.STRIP_LOCATION, 40, 80))
        listOf(png, webp).forEach {
            val exif = ExifInterface(it)
            assertEquals("TestMake", exif.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(exif.latLong)
        }
    }

    @Test
    fun unreadableOriginalStillNormalisesTheOutput() {
        val out = output()
        assertTrue(copier.apply(out, Uri.fromFile(File(folder.root, "missing.jpg")), ExifMode.KEEP, 40, 80))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, ExifInterface(out).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
    }

    @Test
    fun failureReturnsFalseInsteadOfThrowing() {
        val missingOutput = File(folder.root, "nope.jpg")
        assertFalse(copier.apply(missingOutput, Uri.fromFile(original()), ExifMode.KEEP, 40, 80))
    }
}
