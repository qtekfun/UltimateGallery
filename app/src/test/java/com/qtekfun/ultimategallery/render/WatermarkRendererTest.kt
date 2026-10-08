package com.qtekfun.ultimategallery.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WatermarkRendererTest {
    private val logoUri = Uri.parse("file:///logo.png")
    private val red = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private val renderer = WatermarkRenderer { if (it == logoUri) red else null }

    private fun canvasOf(w: Int, h: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return bmp to Canvas(bmp)
    }

    private fun profile(placement: Placement, opacity: Float = 1f, source: WatermarkSource = WatermarkSource.Image(logoUri)) =
        WatermarkProfile(source = source, opacity = opacity, portrait = placement, landscape = placement)

    @Test
    fun imageMarkIsCenteredWhereThePlacementSays() {
        val (bmp, canvas) = canvasOf(200, 100)
        renderer.draw(canvas, 200, 100, profile(Placement(0.25f, 0.5f, 0.2f, 0f)))
        // 40 px wide, 20 px tall (2:1 logo) centered at (50, 50).
        assertEquals(Color.RED, bmp.getPixel(50, 50))
        assertEquals(Color.RED, bmp.getPixel(32, 42))
        assertEquals(Color.WHITE, bmp.getPixel(150, 50))
        assertEquals(Color.WHITE, bmp.getPixel(50, 80))
    }

    @Test
    fun opacityBlendsTheMarkWithThePhoto() {
        val (bmp, canvas) = canvasOf(200, 100)
        renderer.draw(canvas, 200, 100, profile(Placement(0.5f, 0.5f, 0.3f, 0f), opacity = 0.5f))
        val p = bmp.getPixel(100, 50)
        assertEquals(255, Color.red(p))
        assertEquals(128f, Color.green(p).toFloat(), 2f)
    }

    @Test
    fun rotationTurnsTheMark() {
        val (bmp, canvas) = canvasOf(200, 200)
        renderer.draw(canvas, 200, 200, profile(Placement(0.5f, 0.5f, 0.4f, 90f)))
        // 80 x 40 mark turned a quarter: now 40 wide and 80 tall.
        assertEquals(Color.RED, bmp.getPixel(100, 70))
        assertEquals(Color.WHITE, bmp.getPixel(65, 100))
    }

    @Test
    fun theSameProfileGivesTheSamePictureAtAnyResolution() {
        val p = profile(Placement(0.3f, 0.6f, 0.25f, 20f))
        val (small, c1) = canvasOf(200, 100)
        val (large, c2) = canvasOf(800, 400)
        renderer.draw(c1, 200, 100, p)
        renderer.draw(c2, 800, 400, p)
        var mismatches = 0
        val samples = 50
        for (ix in 0 until samples) {
            for (iy in 0 until samples) {
                val sx = ix * 200 / samples
                val sy = iy * 100 / samples
                if ((small.getPixel(sx, sy) == Color.RED) !=
                    (large.getPixel(sx * 4 + 2, sy * 4 + 2) == Color.RED)
                ) {
                    mismatches++
                }
            }
        }
        assertTrue("$mismatches of ${samples * samples} samples differ", mismatches < samples * samples / 50)
    }

    @Test
    fun portraitAndLandscapeUseTheirOwnPlacement() {
        val base = WatermarkProfile(
            source = WatermarkSource.Image(logoUri),
            opacity = 1f,
            portrait = Placement(0.5f, 0.2f, 0.3f, 0f),
            landscape = Placement(0.5f, 0.8f, 0.3f, 0f)
        )
        val (a, ca) = canvasOf(100, 200)
        renderer.draw(ca, 100, 200, base)
        assertEquals(Color.RED, a.getPixel(50, 40))
        assertEquals(Color.WHITE, a.getPixel(50, 160))
        val (b, cb) = canvasOf(200, 100)
        renderer.draw(cb, 200, 100, base)
        assertEquals(Color.RED, b.getPixel(100, 80))
        assertEquals(Color.WHITE, b.getPixel(100, 20))
        val (c, cc) = canvasOf(100, 100)
        renderer.draw(cc, 100, 100, base, Orientation.LANDSCAPE)
        assertEquals(Color.RED, c.getPixel(50, 80))
    }

    @Test
    fun textMarkDrawsPixelsAndHasAWiderThanTallAspect() {
        val src = WatermarkSource.Text("Sample", TextStyleSpec(colorArgb = Color.BLUE, shadowEnabled = false))
        assertTrue(renderer.markAspect(src) > 2f)
        val (bmp, canvas) = canvasOf(400, 200)
        renderer.draw(canvas, 400, 200, src, 1f, Placement(0.5f, 0.5f, 0.5f, 0f))
        var blue = 0
        for (x in 100 until 300) for (y in 80 until 120) if (bmp.getPixel(x, y) == Color.BLUE) blue++
        assertTrue(blue > 100)
        assertEquals(Color.WHITE, bmp.getPixel(5, 5))
    }

    @Test
    fun blankTextDrawsNothing() {
        val (bmp, canvas) = canvasOf(100, 100)
        renderer.draw(canvas, 100, 100, WatermarkSource.Text("  "), 1f, Placement(0.5f, 0.5f, 0.5f, 0f))
        assertEquals(Color.WHITE, bmp.getPixel(50, 50))
    }

    @Test
    fun tiledMarkRepeatsAcrossTheImage() {
        val (bmp, canvas) = canvasOf(400, 400)
        val tiled = WatermarkSource.Tiled(WatermarkSource.Image(logoUri), spacing = 1f, staggerX = 0f)
        renderer.draw(canvas, 400, 400, tiled, 1f, Placement(0.5f, 0.5f, 0.1f, 0f))
        var redPixels = 0
        for (x in 0 until 400 step 4) for (y in 0 until 400 step 4) if (bmp.getPixel(x, y) == Color.RED) redPixels++
        assertTrue("only $redPixels samples are red", redPixels > 100)
        assertEquals(Color.RED, bmp.getPixel(200, 200))
        assertNotEquals(Color.RED, bmp.getPixel(220, 200))
    }

    @Test
    fun missingLogoDrawsNothing() {
        val (bmp, canvas) = canvasOf(50, 50)
        renderer.draw(
            canvas,
            50,
            50,
            WatermarkSource.Image(Uri.parse("file:///missing.png")),
            1f,
            Placement(0.5f, 0.5f, 0.5f, 0f)
        )
        assertEquals(Color.WHITE, bmp.getPixel(25, 25))
    }
}
