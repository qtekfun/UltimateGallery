package com.qtekfun.ultimategallery.render

import android.graphics.Typeface

/**
 * Turns the font, weight and italic choices of a text mark into a [Typeface].
 *
 * Bundled and imported fonts are usually static files with a single weight, so asking for 900 would
 * silently give the regular face. [resolve] reports how far the resolved face is below the requested
 * weight as an [Resolved.emboldenPx] stroke, so the Weight slider is visible on every font.
 */
object MarkTypefaces {
    /** A typeface plus the extra stroke width (in pixels at the given text size) that emulates a missing weight. */
    data class Resolved(val typeface: Typeface, val emboldenPx: Float, val skewX: Float = 0f)

    /** Weight gap, in CSS units, under which the nearest real face is considered good enough. */
    private const val SNAP_GAP = 100
    private const val EMBOLDEN_PER_STEP = 0.012f
    private const val MAX_WEIGHT = 1000
    private const val FAKE_ITALIC_SKEW = -0.25f

    /** The typeface for a [base] face (null means the system default) and the choices, without weight emulation (also used for UI previews). */
    fun create(base: Typeface?, weight: Int, italic: Boolean): Typeface = Typeface.create(base ?: Typeface.DEFAULT, weight.coerceIn(1, MAX_WEIGHT), italic)

    /** Resolves [fontId] through [fonts]; unknown ids and lookups that return null fall back to the system default. */
    fun resolve(fontId: String, fonts: (String) -> Typeface?, weight: Int, italic: Boolean, textSize: Float): Resolved {
        val base = runCatching { fonts(fontId) }.getOrNull()
        // The system default has real weights, so ask for the right one. Bundled and imported fonts are used as they are: asking the
        // framework for another weight of a single-style file only reports the requested weight without changing the glyphs.
        val typeface = base ?: create(null, weight, italic)
        val actual = runCatching { typeface.weight }.getOrDefault(0)
        val gap = weight - actual
        val embolden = if (actual > 0 && gap >= SNAP_GAP) gap / 100f * EMBOLDEN_PER_STEP * textSize else 0f
        // Fonts without an italic face (script, most single-style imports) would ignore the request: slant them instead.
        val skew = if (italic && !typeface.isItalic) FAKE_ITALIC_SKEW else 0f
        return Resolved(typeface, embolden, skew)
    }
}
