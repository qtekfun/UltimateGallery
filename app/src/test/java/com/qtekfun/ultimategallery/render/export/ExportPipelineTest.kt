package com.qtekfun.ultimategallery.render.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportItemResult
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

private class FakeSink : ExportSink {
    val files = LinkedHashMap<String, ByteArray>()
    var lastRelativePath: String? = null

    override suspend fun publish(file: File, displayName: String, mimeType: String, relativePath: String): Pair<Uri, String> {
        files[displayName] = file.readBytes()
        lastRelativePath = relativePath
        return Uri.parse("content://fake/$displayName") to displayName
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ExportPipelineTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val logoUri = Uri.parse("file:///logo.png")
    private val magenta = TestImages.solid(20, 10, Color.MAGENTA)
    private val renderer = WatermarkRenderer { if (it == logoUri) magenta else null }
    private val sink = FakeSink()

    private fun pipeline(budget: Long = ExportImageLoader.DEFAULT_PIXEL_BUDGET) =
        ExportPipeline(context, ExportImageLoader(context, budget), renderer, ExifCopier(context), sink, Dispatchers.Unconfined)

    private fun item(file: File, id: Long = 1, w: Int = 0, h: Int = 0) =
        MediaItem(id, Uri.fromFile(file), file.name, "image/png", false, 0, w, h, file.length(), 0, 1, null)

    private fun profile(
        portrait: Placement = Placement(0.5f, 0.25f, 0.4f, 0f),
        landscape: Placement = Placement(0.25f, 0.5f, 0.2f, 0f),
        export: ExportSettings =
            ExportSettings(format = ExportFormat.PNG, fileNamePattern = "{name}_wm", destination = "Pictures/Test", exif = ExifMode.STRIP_ALL)
    ) = WatermarkProfile(source = WatermarkSource.Image(logoUri), opacity = 1f, portrait = portrait, landscape = landscape, export = export)

    private fun run(item: MediaItem, profile: WatermarkProfile, pipeline: ExportPipeline = pipeline(), stages: MutableList<Float>? = null): ExportItemResult =
        runBlocking { pipeline.exportOne(item, profile, 0, 1) { stages?.add(it) } }

    private fun decode(result: ExportItemResult): Bitmap {
        val bytes = sink.files.getValue(requireNotNull(result.outputName))
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun source(name: String, bitmap: Bitmap): File = TestImages.write(bitmap, folder.newFile(name))

    @Test
    fun landscapeWatermarkLandsWhereThePlacementSays() {
        val result = run(item(source("a.png", TestImages.solid(200, 100, Color.BLUE))), profile())
        assertNotNull(result.outputUri)
        assertEquals("a_wm.png", result.outputName)
        assertEquals("Pictures/Test", sink.lastRelativePath)
        val out = decode(result)
        assertEquals(200, out.width)
        assertEquals(100, out.height)
        assertEquals(Color.MAGENTA, out.getPixel(50, 50))
        assertEquals(Color.BLUE, out.getPixel(150, 50))
    }

    @Test
    fun portraitUsesThePortraitBucket() {
        val result = run(item(source("p.png", TestImages.solid(100, 200, Color.BLUE))), profile())
        val out = decode(result)
        // Portrait placement: 40 px wide mark centered at (50, 50).
        assertEquals(Color.MAGENTA, out.getPixel(50, 50))
        assertEquals(Color.BLUE, out.getPixel(50, 150))
        assertEquals(Color.BLUE, out.getPixel(10, 50))
    }

    @Test
    fun exifRotatedSourceEndsUpUprightInThePortraitBucket() {
        // Stored as 200x100 with the left half green; rotated 90 degrees clockwise it shows as 100x200 with green on top.
        val file = TestImages.writeJpeg(TestImages.halves(200, 100, Color.GREEN, Color.BLUE), folder.newFile("r.jpg"), ExifInterface.ORIENTATION_ROTATE_90)
        val exportSettings = ExportSettings(format = ExportFormat.PNG, destination = "Pictures/Test", exif = ExifMode.STRIP_ALL)
        val out = decode(run(item(file), profile(export = exportSettings)))
        assertEquals(100, out.width)
        assertEquals(200, out.height)
        assertEquals(Color.MAGENTA, out.getPixel(50, 50))
        assertTrue(TestImages.near(Color.GREEN, out.getPixel(10, 90)))
        assertTrue(TestImages.near(Color.BLUE, out.getPixel(10, 190)))
    }

    @Test
    fun maxLongEdgeResizesExactlyAndNeverEnlarges() {
        val export = ExportSettings(format = ExportFormat.PNG, maxLongEdge = 100, destination = "Pictures/Test", exif = ExifMode.STRIP_ALL)
        val big = decode(run(item(source("big.png", TestImages.solid(400, 200, Color.BLUE))), profile(export = export)))
        assertEquals(100, big.width)
        assertEquals(50, big.height)
        val small = decode(run(item(source("small.png", TestImages.solid(60, 30, Color.BLUE))), profile(export = export)))
        assertEquals(60, small.width)
        assertEquals(30, small.height)
    }

    @Test
    fun mixedBatchKeepsEachPhotoOwnSizeOrientationAndPosition() = runBlocking {
        val export = ExportSettings(format = ExportFormat.PNG, fileNamePattern = "b_{n}", destination = "Pictures/Test", exif = ExifMode.STRIP_ALL)
        val files = listOf(
            source("1.png", TestImages.solid(300, 100, Color.BLUE)),
            source("2.png", TestImages.solid(80, 160, Color.BLUE)),
            source("3.png", TestImages.solid(120, 120, Color.BLUE))
        )
        val p = pipeline()
        val results = files.mapIndexed { i, f -> p.exportOne(item(f, i + 1L), profile(export = export), i, files.size) { } }
        assertEquals(listOf("b_1.png", "b_2.png", "b_3.png"), results.map { it.outputName })
        assertEquals(listOf(300, 80, 120), results.map { decode(it).width })
        assertEquals(listOf(100, 160, 120), results.map { decode(it).height })
        // Landscape and square use the landscape mark (20% of the width), portrait the portrait mark (40%).
        assertEquals(Color.MAGENTA, decode(results[0]).getPixel(75, 50))
        assertEquals(Color.MAGENTA, decode(results[1]).getPixel(40, 40))
        assertEquals(Color.MAGENTA, decode(results[2]).getPixel(30, 60))
    }

    @Test
    fun failureOfOneItemDoesNotStopTheOthers() = runBlocking {
        val p = pipeline()
        val good = item(source("ok.png", TestImages.solid(100, 50, Color.BLUE)), 1)
        val bad = item(File(folder.root, "missing.png"), 2)
        val results = listOf(bad, good).mapIndexed { i, photo -> p.exportOne(photo, profile(), i, 2) { } }
        assertNull(results[0].outputUri)
        assertEquals(2L, results[0].sourceId)
        assertNotNull(results[0].error)
        assertNotNull(results[1].outputUri)
        assertNull(results[1].error)
    }

    @Test
    fun imageOverTheBudgetIsDownscaledAndFlagged() {
        val result = run(item(source("huge.png", TestImages.solid(100, 100, Color.BLUE))), profile(), pipeline(budget = 2000))
        assertTrue(result.downscaled)
        val out = decode(result)
        assertEquals(25, out.width)
        assertEquals(25, out.height)
        val normal = run(item(source("fits.png", TestImages.solid(40, 40, Color.BLUE))), profile(), pipeline(budget = 2000))
        assertFalse(normal.downscaled)
    }

    @Test
    fun transparentPngIsFlattenedOnWhiteForJpeg() {
        val clear = TestImages.solid(100, 100, Color.TRANSPARENT)
        val export = ExportSettings(format = ExportFormat.JPEG, destination = "Pictures/Test", exif = ExifMode.STRIP_ALL)
        val result = run(item(source("alpha.png", clear)), profile(export = export))
        assertEquals("alpha_wm.jpg", result.outputName)
        val out = decode(result)
        assertFalse(out.hasAlpha())
        assertTrue(TestImages.near(Color.WHITE, out.getPixel(95, 5), 6))
    }

    @Test
    fun transparentPngStaysTransparentAsPng() {
        val clear = TestImages.solid(100, 100, Color.TRANSPARENT)
        val out = decode(run(item(source("alpha2.png", clear)), profile()))
        assertEquals(0, Color.alpha(out.getPixel(95, 5)))
    }

    @Test
    fun reportsStagesInOrder() {
        val stages = mutableListOf<Float>()
        run(item(source("s.png", TestImages.solid(60, 60, Color.BLUE))), profile(), stages = stages)
        assertEquals(listOf(0.1f, 0.5f, 0.8f, 1f), stages)
    }

    @Test
    fun keepsExifAndTemporaryFilesAreCleanedUp() {
        val file = TestImages.writeJpeg(TestImages.solid(80, 40, Color.BLUE), folder.newFile("e.jpg"))
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Cam")
            setLatLong(10.0, 20.0)
            saveAttributes()
        }
        val export = ExportSettings(format = ExportFormat.JPEG, destination = "Pictures/Test", exif = ExifMode.STRIP_LOCATION)
        val leftBefore = context.cacheDir.listFiles()?.size ?: 0
        val result = run(item(file), profile(export = export))
        val written = folder.newFile("written.jpg").also { it.writeBytes(sink.files.getValue(requireNotNull(result.outputName))) }
        val exif = ExifInterface(written)
        assertEquals("Cam", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.latLong)
        assertEquals(leftBefore, context.cacheDir.listFiles()?.size ?: 0)
    }

    @Test
    fun normalizesDestinationFolders() {
        assertEquals("Pictures/Wallapop/", MediaStoreExportSink.normalizeRelativePath("Pictures/Wallapop"))
        assertEquals("DCIM/Shop/", MediaStoreExportSink.normalizeRelativePath("/DCIM/Shop/"))
        assertEquals("Pictures/Other/", MediaStoreExportSink.normalizeRelativePath("Other"))
        assertEquals("Pictures/", MediaStoreExportSink.normalizeRelativePath(""))
        assertEquals("Pictures/Pictures2/", MediaStoreExportSink.normalizeRelativePath("Pictures2"))
        assertEquals("Pictures/a/b/", MediaStoreExportSink.normalizeRelativePath("../a//b"))
    }
}
