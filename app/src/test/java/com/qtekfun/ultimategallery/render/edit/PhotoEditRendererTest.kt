package com.qtekfun.ultimategallery.render.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.qtekfun.ultimategallery.domain.edit.Adjustments
import com.qtekfun.ultimategallery.domain.edit.Annotation
import com.qtekfun.ultimategallery.domain.edit.BackgroundEdit
import com.qtekfun.ultimategallery.domain.edit.BackgroundFill
import com.qtekfun.ultimategallery.domain.edit.Cover
import com.qtekfun.ultimategallery.domain.edit.CoverMode
import com.qtekfun.ultimategallery.domain.edit.EditPoint
import com.qtekfun.ultimategallery.domain.edit.FilterChoice
import com.qtekfun.ultimategallery.domain.edit.FilterPreset
import com.qtekfun.ultimategallery.domain.edit.MaskOp
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.domain.edit.ShapeKind
import com.qtekfun.ultimategallery.render.export.TestImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoEditRendererTest {
    private val renderer = PhotoEditRenderer { null }

    private fun solid(width: Int, height: Int, color: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** A white photo with a red square in its middle half. */
    private fun product(size: Int = 80): Bitmap = solid(size, size, Color.WHITE).also { b ->
        Canvas(b).drawRect(size / 4f, size / 4f, 3 * size / 4f, 3 * size / 4f, Paint().apply { color = RED })
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 6) {
        assertTrue("expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}", TestImages.near(expected, actual, tolerance))
    }

    @Test
    fun anEmptyEditKeepsThePixels() {
        val src = product()
        val out = renderer.render(src, PhotoEdit())
        assertEquals(src.getPixel(40, 40), out.getPixel(40, 40))
        assertEquals(src.getPixel(2, 2), out.getPixel(2, 2))
    }

    @Test
    fun theMonoFilterTurnsColorsGray() {
        val out = renderer.render(product(), PhotoEdit(filter = FilterChoice(FilterPreset.MONO)))
        val p = out.getPixel(40, 40)
        assertEquals(Color.red(p).toFloat(), Color.green(p).toFloat(), 2f)
        assertEquals(Color.green(p).toFloat(), Color.blue(p).toFloat(), 2f)
    }

    @Test
    fun theVignetteDarkensCornersOnly() {
        val out = renderer.render(solid(100, 100, Color.WHITE), PhotoEdit(adjust = Adjustments(vignette = 1f)))
        assertNear(Color.WHITE, out.getPixel(50, 50), 2)
        assertTrue(Color.red(out.getPixel(0, 0)) < 128)
    }

    @Test
    fun sharpeningKeepsFlatAreas() {
        val out = renderer.render(solid(300, 300, GRAY), PhotoEdit(adjust = Adjustments(sharpness = 1f)))
        assertNear(GRAY, out.getPixel(150, 299), 1)
    }

    @Test
    fun autoBackgroundBecomesTransparentOrTheFillColor() {
        val auto = BackgroundEdit(listOf(MaskOp.Auto(0.2f)), feather = 0f)
        val clear = renderer.render(product(), PhotoEdit(background = auto))
        assertEquals(0, Color.alpha(clear.getPixel(3, 3)))
        assertEquals(255, Color.alpha(clear.getPixel(40, 40)))
        assertNear(RED, clear.getPixel(40, 40))

        val blue = renderer.render(product(), PhotoEdit(background = auto.copy(fill = BackgroundFill.Solid(BLUE))))
        assertEquals(255, Color.alpha(blue.getPixel(3, 3)))
        assertNear(BLUE, blue.getPixel(3, 3))
        assertNear(RED, blue.getPixel(40, 40))
    }

    @Test
    fun coversBlurOnlyWhatTheyPaint() {
        // Left half black, right half white; a blur stroke crosses the border at the top.
        val src = solid(200, 200, Color.WHITE).also { Canvas(it).drawRect(0f, 0f, 100f, 200f, Paint().apply { color = Color.BLACK }) }
        val cover = Cover(listOf(EditPoint(0.3f, 0.2f), EditPoint(0.7f, 0.2f)), width = 0.15f, mode = CoverMode.BLUR, strength = 1f)
        val out = renderer.render(src, PhotoEdit(covers = listOf(cover)))
        val blurred = out.getPixel(101, 40)
        assertTrue("border inside the stroke should be gray: ${Integer.toHexString(blurred)}", Color.red(blurred) in 30..225)
        assertNear(Color.WHITE, out.getPixel(101, 160), 0)
        assertNear(Color.BLACK, out.getPixel(98, 160), 0)
    }

    @Test
    fun pixelationMakesFlatBlocks() {
        val src = solid(200, 200, Color.WHITE).also { b ->
            val paint = Paint().apply { color = Color.BLACK }
            for (x in 0 until 200 step 2) Canvas(b).drawRect(x.toFloat(), 0f, x + 1f, 200f, paint)
        }
        val cover = Cover(listOf(EditPoint(0.5f, 0.5f)), width = 0.5f, mode = CoverMode.PIXELATE, strength = 0.5f)
        val out = renderer.render(src, PhotoEdit(covers = listOf(cover)))
        assertEquals(out.getPixel(100, 100), out.getPixel(101, 100))
    }

    @Test
    fun annotationsLandOnTheSameFractionAtEverySize() {
        val box = Annotation.Shape(1, ShapeKind.RECTANGLE, EditPoint(0.6f, 0.6f), EditPoint(0.9f, 0.9f), BLUE, 0.02f, filled = true)
        listOf(100 to 80, 400 to 320).forEach { (w, h) ->
            val out = renderer.render(solid(w, h, Color.WHITE), PhotoEdit(annotations = listOf(box)))
            assertNear(BLUE, out.getPixel((0.75f * w).toInt(), (0.75f * h).toInt()), 0)
            assertNear(Color.WHITE, out.getPixel((0.5f * w).toInt(), (0.5f * h).toInt()), 0)
        }
    }

    @Test
    fun textIsDrawnAroundItsCenter() {
        val text = Annotation.Text(1, "MMMM", EditPoint(0.5f, 0.5f), 0.2f, 0f, Color.BLACK, "default", boxed = true)
        val out = renderer.render(solid(200, 200, Color.WHITE), PhotoEdit(annotations = listOf(text)))
        val (hw, hh) = renderer.painter.halfSize(text, 200, 200)
        assertTrue(hw > 10f && hh > 10f)
        // The light box under dark text covers the center region; outside it stays white.
        var dark = 0
        for (x in (100 - hw.toInt() + 2) until (100 + hw.toInt() - 2)) if (Color.red(out.getPixel(x, 100)) < 100) dark++
        assertTrue("some glyph pixels on the middle row", dark > 0)
        assertNear(Color.WHITE, out.getPixel(2, 2), 0)
    }

    private companion object {
        val RED = Color.rgb(208, 32, 32)
        val BLUE = Color.rgb(32, 32, 208)
        val GRAY = Color.rgb(128, 128, 128)
    }
}
