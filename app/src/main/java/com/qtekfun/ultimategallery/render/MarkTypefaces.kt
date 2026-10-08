package com.qtekfun.ultimategallery.render

import android.graphics.Typeface
import com.qtekfun.ultimategallery.domain.watermark.MarkFont

/**
 * Turns the font, weight and italic choices of a text mark into a [Typeface].
 *
 * System families only ship a few weights (monospace and cursive often just one), so asking for 900
 * would silently give the regular face. [resolve] reports how far the resolved face is below the
 * requested weight as an [Resolved.emboldenPx] stroke, so the Weight slider is visible on every font.
 */
object MarkTypefaces {
    /** A typeface plus the extra stroke width (in pixels at the given text size) that emulates a missing weight. */
    data class Resolved(val typeface: Typeface, val emboldenPx: Float, val skewX: Float = 0f)

    /** Weight gap, in CSS units, under which the nearest real face is considered good enough. */
    private const val SNAP_GAP = 100
    private const val EMBOLDEN_PER_STEP = 0.012f
    private const val MAX_WEIGHT = 1000
    private const val FAKE_ITALIC_SKEW = -0.25f

    fun familyOf(font: MarkFont): String = when (font) {
        MarkFont.SANS -> "sans-serif"
        MarkFont.SERIF -> "serif"
        MarkFont.MONOSPACE -> "monospace"
        MarkFont.CURSIVE -> "cursive"
        MarkFont.CONDENSED -> "sans-serif-condensed"
    }

    /** The system typeface for the choices, without weight emulation (also used for UI previews). */
    fun create(font: MarkFont, weight: Int, italic: Boolean): Typeface =
        Typeface.create(Typeface.create(familyOf(font), Typeface.NORMAL), weight.coerceIn(1, MAX_WEIGHT), italic)

    fun resolve(font: MarkFont, weight: Int, italic: Boolean, textSize: Float): Resolved {
        val typeface = create(font, weight, italic)
        val actual = runCatching { typeface.weight }.getOrDefault(0)
        val gap = weight - actual
        val embolden = if (actual > 0 && gap >= SNAP_GAP) gap / 100f * EMBOLDEN_PER_STEP * textSize else 0f
        // Families without an italic face (script, monospace on many devices) would ignore the request: slant them instead.
        val skew = if (italic && !typeface.isItalic) FAKE_ITALIC_SKEW else 0f
        return Resolved(typeface, embolden, skew)
    }
}
