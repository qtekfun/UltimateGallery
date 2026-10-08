package com.qtekfun.ultimategallery.data.edit

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.render.export.ExifCopier
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import com.qtekfun.ultimategallery.render.export.TestImages
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
class LosslessOrientationTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val editor = ImageEditor(context, ExportImageLoader(context), ExifCopier(context), Dispatchers.Unconfined)

    private fun orientationOf(file: File) = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    /** The compressed image data, which starts after the start-of-scan marker. */
    private fun scanData(file: File): ByteArray {
        val bytes = file.readBytes()
        var i = 0
        while (i < bytes.size - 1 && !(bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xDA.toByte())) i++
        return bytes.copyOfRange(i, bytes.size)
    }

    private fun jpeg(orientation: Int = ExifInterface.ORIENTATION_NORMAL) = writeJpeg(quadrants(64, 32), folder.newFile(), orientation)

    @Test
    fun fourClockwiseTurnsComposeBackToNormal() {
        val file = jpeg()
        val seen = (1..4).map {
            LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(quarterTurns = 1))
            orientationOf(file)
        }
        assertEquals(
            listOf(
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_NORMAL
            ),
            seen
        )
    }

    @Test
    fun fourCounterClockwiseTurnsComposeBackToNormal() {
        val file = jpeg()
        val seen = (1..4).map {
            LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(quarterTurns = 3))
            orientationOf(file)
        }
        assertEquals(
            listOf(
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_NORMAL
            ),
            seen
        )
    }

    @Test
    fun turnsComposeWithTheStoredOrientation() {
        val file = jpeg(ExifInterface.ORIENTATION_ROTATE_90)
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(quarterTurns = 1))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_180, orientationOf(file))
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(quarterTurns = 3))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, orientationOf(file))
    }

    @Test
    fun flipsComposeThroughTheOrientationValues() {
        val file = jpeg()
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(flipH = true))
        assertEquals(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, orientationOf(file))
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(flipH = true))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, orientationOf(file))
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(flipV = true))
        assertEquals(ExifInterface.ORIENTATION_FLIP_VERTICAL, orientationOf(file))
    }

    @Test
    fun theImageDataIsNotTouched() {
        val file = jpeg()
        val before = scanData(file)
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(quarterTurns = 1))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, orientationOf(file))
        assertArrayEquals(before, scanData(file))
    }

    @Test
    fun theShownPhotoMatchesAFullRender() {
        val loader = ExportImageLoader(context)
        for (spec in listOf(CropRotateSpec(quarterTurns = 1), CropRotateSpec(quarterTurns = 3), CropRotateSpec(quarterTurns = 2, flipH = true))) {
            val file = jpeg()
            LosslessOrientation.apply(ExifInterface(file), spec)
            val shown = loader.load(Uri.fromFile(file), null).bitmap
            val colors = quadrantColors(shown)
            val rendered = runBlocking { editor.render(Uri.fromFile(jpeg()), "image/jpeg", spec) }
            val reference = quadrantColors(BitmapFactory.decodeFile(rendered.file.path))
            assertEquals(rendered.width, shown.width)
            assertEquals(rendered.height, shown.height)
            reference.indices.forEach { i ->
                assertTrue("$spec quadrant $i", TestImages.near(reference[i], colors[i], 40))
            }
        }
    }

    @Test
    fun onlyOrientationOnlyJpegSpecsAreLossless() {
        assertTrue(ImageEditor.isLossless("image/jpeg", CropRotateSpec(quarterTurns = 1)))
        assertTrue(ImageEditor.isLossless("image/jpeg", CropRotateSpec(flipH = true)))
        assertFalse(ImageEditor.isLossless("image/png", CropRotateSpec(quarterTurns = 1)))
        assertFalse(ImageEditor.isLossless("image/jpeg", CropRotateSpec(quarterTurns = 1, straightenDeg = 1f)))
        assertFalse(ImageEditor.isLossless("image/jpeg", CropRotateSpec(crop = CropRect(0f, 0f, 0.5f, 1f))))
    }

    @Test
    fun pixelDimensionTagsSwapOnOddTurns() {
        val file = jpeg()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, "64")
            setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, "32")
            saveAttributes()
        }
        LosslessOrientation.apply(ExifInterface(file), CropRotateSpec(quarterTurns = 1))
        val exif = ExifInterface(file)
        assertEquals("32", exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION))
        assertEquals("64", exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION))
    }
}
