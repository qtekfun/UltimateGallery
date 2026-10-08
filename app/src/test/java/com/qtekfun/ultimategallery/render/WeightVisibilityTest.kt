package com.qtekfun.ultimategallery.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.qtekfun.ultimategallery.domain.watermark.MarkFont
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Weight slider must be visible for every font, including families that ship a single weight. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WeightVisibilityTest {
    private val renderer = WatermarkRenderer { null }

    private fun inkOf(style: TextStyleSpec): Int {
        val bmp = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        renderer.draw(Canvas(bmp), 400, 100, WatermarkSource.Text("Wallapop", style), 1f, Placement(0.5f, 0.5f, 0.6f, 0f))
        var ink = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) ink += 255 - Color.red(bmp.getPixel(x, y))
        return ink
    }

    @Test
    fun heavierWeightPutsMoreInkOnEveryFont() {
        MarkFont.entries.forEach { font ->
            val base = TextStyleSpec(font = font, colorArgb = Color.BLACK, shadowEnabled = false)
            val light = inkOf(base.copy(weight = 300))
            val heavy = inkOf(base.copy(weight = 900))
            assertTrue("$font: weight 900 ($heavy) should be darker than 300 ($light)", heavy > light)
        }
    }

    @Test
    fun missingWeightsAreEmulatedWithAStroke() {
        // Regardless of the weights a family ships, asking far above the resolved weight emboldens.
        val resolved = MarkTypefaces.resolve(MarkFont.MONOSPACE, 900, false, 100f)
        val actual = runCatching { resolved.typeface.weight }.getOrDefault(0)
        if (actual in 1..700) assertTrue(resolved.emboldenPx > 0f)
        assertTrue(MarkTypefaces.resolve(MarkFont.MONOSPACE, 400, false, 100f).emboldenPx == 0f)
    }
}
