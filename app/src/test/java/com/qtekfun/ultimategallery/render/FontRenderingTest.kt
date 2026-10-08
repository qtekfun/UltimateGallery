package com.qtekfun.ultimategallery.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FontRenderingTest {
    private val renderer = WatermarkRenderer({ null }, TestFonts.lookup)

    private fun pixels(fontId: String, r: WatermarkRenderer = renderer): IntArray {
        val bmp = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.GRAY)
        val style = TextStyleSpec(fontId = fontId, shadowEnabled = false)
        r.draw(Canvas(bmp), 600, 200, WatermarkSource.Text("Wallapop Hill", style), 1f, Placement(0.5f, 0.5f, 0.8f, 0f))
        return IntArray(600 * 200).also { bmp.getPixels(it, 0, 600, 0, 0, 600, 200) }
    }

    @Test
    fun differentBundledFontsDrawDifferentPixels() {
        val rendered = TestFonts.bundledIds.map { pixels(it).toList() }
        assertEquals("every font looks different", rendered.size, rendered.toSet().size)
        assertFalse(rendered.contains(pixels(FontIds.DEFAULT).toList()))
    }

    @Test
    fun unknownIdsAndMissingLookupsFallBackToTheDefaultFont() {
        val default = pixels(FontIds.DEFAULT)
        assertArrayEquals(default, pixels("bundled:DoesNotExist"))
        assertArrayEquals(default, pixels("imported:gone.ttf"))
        assertArrayEquals(default, pixels(TestFonts.bundledIds.first(), WatermarkRenderer({ null })))
        assertArrayEquals(default, pixels(TestFonts.bundledIds.first(), WatermarkRenderer({ null }, { error("boom") })))
    }

    @Test
    fun resolveUsesTheLookupAndFallsBackToDefault() {
        val base = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val resolved = MarkTypefaces.resolve("x", { base }, 400, false, 100f)
        assertTrue(resolved.skewX == 0f)
        val fallback = MarkTypefaces.resolve("x", { null }, 400, false, 100f)
        assertEquals(400, fallback.typeface.weight)
    }

    @Test
    fun staticFontsGetWeightAndItalicEmulation() {
        val id = FontIds.bundled("Pacifico")
        val bold = MarkTypefaces.resolve(id, TestFonts.lookup, 900, false, 100f)
        assertTrue("weight stroke", bold.emboldenPx > 0f)
        val italic = MarkTypefaces.resolve(id, TestFonts.lookup, 400, true, 100f)
        assertEquals(-0.25f, italic.skewX)
    }
}
