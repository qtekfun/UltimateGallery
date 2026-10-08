package com.qtekfun.ultimategallery.render.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.profile.ProfileCodec
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.watermark.ExifMode
import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.MarkFont
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The job file hands the profile to the worker through [ProfileCodec]; styled text must survive that. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StyledTextExportTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val files = LinkedHashMap<String, ByteArray>()
    private val sink = object : ExportSink {
        override suspend fun publish(file: File, displayName: String, mimeType: String, relativePath: String): Pair<Uri, String> {
            files[displayName] = file.readBytes()
            return Uri.parse("content://fake/$displayName") to displayName
        }
    }
    private val pipeline = ExportPipeline(
        context,
        ExportImageLoader(context, ExportImageLoader.DEFAULT_PIXEL_BUDGET),
        WatermarkRenderer { null },
        ExifCopier(context),
        sink,
        Dispatchers.Unconfined
    )
    private val placement = Placement(0.5f, 0.5f, 0.8f, 0f)

    private fun export(profile: WatermarkProfile): Bitmap {
        val file = TestImages.write(TestImages.solid(400, 200, Color.GRAY), folder.newFile())
        val item = MediaItem(1, Uri.fromFile(file), file.name, "image/png", false, 0, 0, 0, file.length(), 0, 1, null)
        val result = runBlocking { pipeline.exportOne(item, profile, 0, 1) { } }
        val bytes = files.getValue(requireNotNull(result.outputName))
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun profile(style: TextStyleSpec) = WatermarkProfile(
        source = WatermarkSource.Text("Wallapop", style),
        opacity = 1f,
        portrait = placement,
        landscape = placement,
        export = ExportSettings(format = ExportFormat.PNG, destination = "Pictures/Test", exif = ExifMode.STRIP_ALL)
    )

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private val styled = TextStyleSpec(
        font = MarkFont.SERIF,
        weight = 900,
        italic = true,
        colorArgb = Color.YELLOW,
        outlineEnabled = true,
        outlineColorArgb = Color.RED,
        outlineWidth = 0.12f,
        shadowEnabled = true,
        shadowColorArgb = 0xCC0000FF.toInt(),
        shadowRadius = 0.2f,
        backgroundEnabled = true,
        backgroundColorArgb = 0xCC112233.toInt(),
        backgroundPadding = 0.5f
    )

    @Test
    fun exportOfADecodedJobProfileMatchesExportOfTheOriginal() {
        val original = profile(styled)
        val viaJob = ProfileCodec.fromJson(ProfileCodec.toJson(original))
        assertEquals(original, viaJob)
        assertArrayEquals(pixels(export(original)), pixels(export(viaJob)))
    }

    @Test
    fun everyStyleFieldChangesTheExportedPixels() {
        val base = TextStyleSpec(shadowEnabled = false)
        val reference = pixels(export(profile(base)))
        val variants = mapOf(
            "font" to base.copy(font = MarkFont.MONOSPACE),
            "weight" to base.copy(weight = 900),
            "italic" to base.copy(italic = true),
            "color" to base.copy(colorArgb = Color.GREEN),
            "outline" to base.copy(outlineEnabled = true, outlineColorArgb = Color.RED),
            "shadow" to base.copy(shadowEnabled = true, shadowColorArgb = Color.BLUE, shadowRadius = 0.2f),
            "background" to base.copy(backgroundEnabled = true, backgroundColorArgb = Color.MAGENTA)
        )
        variants.forEach { (name, style) ->
            val out = pixels(export(profile(style)))
            assertFalse("$name should change the export", reference.contentEquals(out))
        }
    }
}
