package com.qtekfun.ultimategallery.domain.edit

import kotlin.math.pow

/**
 * 4×5 color matrices, row major, in the layout of `android.graphics.ColorMatrix` and Compose `ColorMatrix`: each output channel
 * is `a·R + b·G + c·B + d·A + e`, with channels and the offset `e` in 0..255.
 */
@Suppress("TooManyFunctions")
object ColorMatrices {
    private const val SIZE = 20
    private const val ROW = 5
    private const val OFFSET = 4
    private const val CHANNELS = 3
    private const val MID = 127.5f
    private const val BRIGHTNESS_RANGE = 76f
    private const val EXPOSURE_STOPS = 1.5f
    private const val CONTRAST_DOWN = 0.7f
    private const val WARMTH_RED = 0.16f
    private const val WARMTH_GREEN = 0.04f
    private const val WARMTH_BLUE = 0.16f
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    fun identity(): FloatArray = FloatArray(SIZE).also { m -> for (i in 0 until 4) m[i * ROW + i] = 1f }

    /** The matrix that applies [first] and then [second]. */
    fun then(first: FloatArray, second: FloatArray): FloatArray {
        val out = FloatArray(SIZE)
        for (row in 0 until 4) {
            for (col in 0 until ROW) {
                var sum = 0f
                for (k in 0 until 4) sum += second[row * ROW + k] * first[k * ROW + col]
                if (col == OFFSET) sum += second[row * ROW + OFFSET]
                out[row * ROW + col] = sum
            }
        }
        return out
    }

    /** [matrix] blended with the identity: 0 gives the identity, 1 gives [matrix]. */
    fun blend(matrix: FloatArray, amount: Float): FloatArray {
        val t = amount.coerceIn(0f, 1f)
        val id = identity()
        return FloatArray(SIZE) { id[it] + (matrix[it] - id[it]) * t }
    }

    /** Shifts every channel by up to about 30 % of the range. */
    fun brightness(value: Float): FloatArray = identity().also { m -> for (c in 0 until CHANNELS) m[c * ROW + OFFSET] = value * BRIGHTNESS_RANGE }

    /** Multiplies every channel by 2^(1.5 · [value]): plus or minus one and a half stops. */
    fun exposure(value: Float): FloatArray = scale(2f.pow(value * EXPOSURE_STOPS))

    /** Stretches (positive) or flattens (negative) the channels around the middle gray. */
    fun contrast(value: Float): FloatArray {
        val k = if (value >= 0f) 1f + value else 1f + value * CONTRAST_DOWN
        return scale(k).also { m -> for (c in 0 until CHANNELS) m[c * ROW + OFFSET] = MID * (1f - k) }
    }

    /** -1 is grayscale, 0 leaves the photo alone, 1 doubles the saturation. */
    fun saturation(value: Float): FloatArray = saturationFactor(1f + value)

    /** Positive values warm the photo (more red, less blue), negative values cool it. */
    fun warmth(value: Float): FloatArray = identity().also { m ->
        m[0] = 1f + value * WARMTH_RED
        m[ROW + 1] = 1f + value * WARMTH_GREEN
        m[2 * ROW + 2] = 1f - value * WARMTH_BLUE
    }

    /** The color part of [adjust] (sharpness and vignette are not color operations). */
    fun forAdjustments(adjust: Adjustments): FloatArray {
        var m = identity()
        if (adjust.exposure != 0f) m = then(m, exposure(adjust.exposure))
        if (adjust.brightness != 0f) m = then(m, brightness(adjust.brightness))
        if (adjust.contrast != 0f) m = then(m, contrast(adjust.contrast))
        if (adjust.saturation != 0f) m = then(m, saturation(adjust.saturation))
        if (adjust.warmth != 0f) m = then(m, warmth(adjust.warmth))
        return m
    }

    /** The matrix of [preset] at full intensity. */
    fun forPreset(preset: FilterPreset): FloatArray = when (preset) {
        FilterPreset.NONE -> identity()
        FilterPreset.MONO -> saturationFactor(0f)
        FilterPreset.SEPIA -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        FilterPreset.WARM -> then(warmth(0.6f), saturation(0.1f))
        FilterPreset.COOL -> then(warmth(-0.6f), saturation(-0.05f))
        FilterPreset.VIVID -> then(saturation(0.5f), contrast(0.15f))
        FilterPreset.FADE -> then(then(contrast(-0.35f), brightness(0.1f)), saturation(-0.3f))
        FilterPreset.NOIR -> then(saturationFactor(0f), contrast(0.5f))
    }

    fun forFilter(filter: FilterChoice): FloatArray = if (filter.isIdentity) identity() else blend(forPreset(filter.preset), filter.intensity)

    /** Adjustments first, then the filter: the whole color step of an edit. */
    fun forEdit(adjust: Adjustments, filter: FilterChoice): FloatArray = then(forAdjustments(adjust), forFilter(filter))

    fun isIdentity(matrix: FloatArray): Boolean = matrix.contentEquals(identity())

    /** Applies [matrix] to an opaque or translucent ARGB color, clamping each channel. */
    fun apply(matrix: FloatArray, argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val a = (argb ushr 24) and 0xFF
        fun channel(row: Int): Int {
            val o = row * ROW
            val v = matrix[o] * r + matrix[o + 1] * g + matrix[o + 2] * b + matrix[o + 3] * a + matrix[o + OFFSET]
            return v.coerceIn(0f, 255f).toInt()
        }
        return (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }

    private fun scale(k: Float): FloatArray = identity().also { m -> for (c in 0 until CHANNELS) m[c * ROW + c] = k }

    private fun saturationFactor(s: Float): FloatArray {
        val inv = 1f - s
        val r = LUMA_R * inv
        val g = LUMA_G * inv
        val b = LUMA_B * inv
        return floatArrayOf(
            r + s, g, b, 0f, 0f,
            r, g + s, b, 0f, 0f,
            r, g, b + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
}
