package com.qtekfun.ultimategallery.data.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.render.export.ExifCopier
import com.qtekfun.ultimategallery.render.export.ExportImageLoader
import com.qtekfun.ultimategallery.render.export.TestImages
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class ImageEditorTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val editor = ImageEditor(context, ExportImageLoader(context), ExifCopier(context), Dispatchers.Unconfined)

    private fun render(file: File, mime: String, spec: CropRotateSpec): Pair<EditedImage, Bitmap> = runBlocking {
        val edited = editor.render(Uri.fromFile(file), mime, spec)
        edited to requireNotNull(BitmapFactory.decodeFile(edited.file.path))
    }

    private fun png(width: Int = 40, height: Int = 20) = writePng(quadrants(width, height), folder.newFile())

    private fun assertQuadrants(expected: List<Int>, bitmap: Bitmap) {
        val actual = quadrantColors(bitmap)
        expected.indices.forEach { i ->
            assertTrue(
                "quadrant $i expected ${Integer.toHexString(expected[i])} but was ${Integer.toHexString(actual[i])}",
                TestImages.near(expected[i], actual[i], 8)
            )
        }
    }

    private val r = Quadrant.TOP_LEFT
    private val g = Quadrant.TOP_RIGHT
    private val b = Quadrant.BOTTOM_LEFT
    private val y = Quadrant.BOTTOM_RIGHT

    @Test
    fun identityKeepsThePhoto() {
        val (edited, bitmap) = render(png(), "image/png", CropRotateSpec())
        assertEquals(40, bitmap.width)
        assertEquals(20, bitmap.height)
        assertEquals(40, edited.width)
        assertQuadrants(listOf(r, g, b, y), bitmap)
    }

    @Test
    fun clockwiseTurnSwapsTheSidesAndMovesTheQuadrants() {
        val (_, bitmap) = render(png(), "image/png", CropRotateSpec(quarterTurns = 1))
        assertEquals(20, bitmap.width)
        assertEquals(40, bitmap.height)
        assertQuadrants(listOf(b, r, y, g), bitmap)
    }

    @Test
    fun counterClockwiseTurnAndHalfTurn() {
        assertQuadrants(listOf(g, y, r, b), render(png(), "image/png", CropRotateSpec(quarterTurns = 3)).second)
        assertQuadrants(listOf(y, b, g, r), render(png(), "image/png", CropRotateSpec(quarterTurns = 2)).second)
    }

    @Test
    fun flipsMirrorThePhotoAfterTheTurn() {
        assertQuadrants(listOf(g, r, y, b), render(png(), "image/png", CropRotateSpec(flipH = true)).second)
        assertQuadrants(listOf(b, y, r, g), render(png(), "image/png", CropRotateSpec(flipV = true)).second)
        // Turn clockwise (b r / y g) and mirror horizontally (r b / g y).
        assertQuadrants(listOf(r, b, g, y), render(png(), "image/png", CropRotateSpec(quarterTurns = 1, flipH = true)).second)
    }

    @Test
    fun cropKeepsOnlyTheRequestedRectangle() {
        val spec = CropRotateSpec(crop = CropRect(0.5f, 0f, 1f, 1f))
        val (edited, bitmap) = render(png(), "image/png", spec)
        assertEquals(20, bitmap.width)
        assertEquals(20, bitmap.height)
        assertEquals(20, edited.width)
        assertQuadrants(listOf(g, g, y, y), bitmap)
    }

    @Test
    fun cropIsExpressedOnTheTurnedCanvas() {
        // After a clockwise turn the canvas is b r / y g; keeping its top half leaves b and r.
        val spec = CropRotateSpec(quarterTurns = 1, crop = CropRect(0f, 0f, 1f, 0.5f))
        val (_, bitmap) = render(png(), "image/png", spec)
        assertEquals(20, bitmap.width)
        assertEquals(20, bitmap.height)
        assertQuadrants(listOf(b, r, b, r), bitmap)
    }

    @Test
    fun straightenedPhotoHasNoEmptyCorners() {
        val solid = writePng(TestImages.solid(200, 100, Color.RED), folder.newFile())
        val angle = 12f
        val canvas = com.qtekfun.ultimategallery.feature.edit.CanvasSize(200f, 100f)
        val crop = com.qtekfun.ultimategallery.feature.edit.CropGeometry.largestInscribed(canvas, angle)
        val (_, bitmap) = render(solid, "image/png", CropRotateSpec(straightenDeg = angle, crop = crop))
        assertTrue(bitmap.width < 200 && bitmap.height < 100)
        listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1).forEach { (x, yy) ->
            assertEquals("corner $x,$yy", Color.RED, bitmap.getPixel(x, yy))
        }
    }

    @Test
    fun outputUsesTheSourceFormat() {
        val jpeg = writeJpeg(quadrants(64, 32), folder.newFile("a.jpg"))
        val (jpegEdit, _) = render(jpeg, "image/jpeg", CropRotateSpec(quarterTurns = 1))
        assertEquals("image/jpeg", jpegEdit.format.mimeType)
        assertEquals("jpg", jpegEdit.format.extension)
        val (pngEdit, _) = render(png(), "image/png", CropRotateSpec(quarterTurns = 1))
        assertEquals("image/png", pngEdit.format.mimeType)
        assertEquals("image/jpeg", ImageEditor.formatFor("image/heic").mimeType)
        assertEquals("image/webp", ImageEditor.formatFor("image/webp").mimeType)
        assertEquals(Bitmap.CompressFormat.WEBP_LOSSY, ImageEditor.formatFor("image/webp").compressFormat)
        assertTrue(ImageEditor.keepsFormat("image/webp"))
        assertTrue(!ImageEditor.keepsFormat("image/heic"))
    }

    @Test
    fun jpegEditKeepsCameraDataAndResetsTheOrientation() {
        val jpeg = writeJpeg(quadrants(64, 32), folder.newFile("a.jpg"), ExifInterface.ORIENTATION_ROTATE_90)
        ExifInterface(jpeg).apply {
            setAttribute(ExifInterface.TAG_MAKE, "TestMake")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:05:06 07:08:09")
            saveAttributes()
        }
        // Stored 64 x 32 with orientation 6 is shown 32 x 64; cropping its top half gives 32 x 32.
        val spec = CropRotateSpec(crop = CropRect(0f, 0f, 1f, 0.5f))
        val (edited, bitmap) = render(jpeg, "image/jpeg", spec)
        assertEquals(32, bitmap.width)
        assertEquals(32, bitmap.height)
        val exif = ExifInterface(edited.file)
        assertEquals("TestMake", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("2024:05:06 07:08:09", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        assertNotNull(exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION))
    }
}
