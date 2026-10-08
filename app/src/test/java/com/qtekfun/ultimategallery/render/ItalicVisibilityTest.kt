package com.qtekfun.ultimategallery.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ItalicVisibilityTest {
    private val renderer = WatermarkRenderer({ null }, TestFonts.lookup)

    private fun render(fontId: String, italic: Boolean): List<Int> {
        val bmp = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.GRAY)
        val style = TextStyleSpec(fontId = fontId, italic = italic, shadowEnabled = false)
        renderer.draw(Canvas(bmp), 600, 200, WatermarkSource.Text("Sample Hill", style), 1f, Placement(0.5f, 0.5f, 0.8f, 0f))
        return IntArray(600 * 200).also { bmp.getPixels(it, 0, 600, 0, 0, 600, 200) }.toList()
    }

    @Test
    fun italicChangesTheOutputForEveryFont() {
        (listOf(FontIds.DEFAULT) + TestFonts.bundledIds).forEach { font ->
            assertNotEquals("italic $font", render(font, false), render(font, true))
        }
    }
}
