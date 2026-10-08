package com.qtekfun.ultimategallery.data.media

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MetadataStripperTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun jpeg(): File {
        val file = tmp.newFile("p.jpg")
        file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    @Test
    fun stripRemovesCameraAndLocationTagsButKeepsOrientation() {
        val file = jpeg()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Acme")
            setAttribute(ExifInterface.TAG_MODEL, "X1")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:08 10:00:00")
            setLatLong(41.38, 2.17)
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        assertTrue(ExifInterface(file).latLong != null)

        MetadataStripper.strip(file)

        val after = ExifInterface(file)
        assertNull(after.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(after.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(after.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNull(after.latLong)
        assertNull(after.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, after.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
    }

    @Test
    fun supportedTypes() {
        assertTrue(MetadataStripper.isSupported("image/JPEG"))
        assertTrue(MetadataStripper.isSupported("image/png"))
        assertTrue(MetadataStripper.isSupported("image/webp"))
        assertFalse(MetadataStripper.isSupported("image/gif"))
        assertFalse(MetadataStripper.isSupported("video/mp4"))
    }

    @Test
    fun tagListHasNoDuplicatesAndCoversGps() {
        assertEquals(MetadataStripper.AllTags.size, MetadataStripper.AllTags.toSet().size)
        assertTrue(MetadataStripper.AllTags.count { it.startsWith("GPS") } >= 20)
    }
}
