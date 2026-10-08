package com.qtekfun.ultimategallery.data.edit

/** A rectangle in fractions (0..1) of the edited canvas, with the origin at the top left. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** True when this rectangle covers the whole canvas (within rounding). */
    val isFull: Boolean
        get() = left <= FULL_TOLERANCE && top <= FULL_TOLERANCE && right >= 1f - FULL_TOLERANCE && bottom >= 1f - FULL_TOLERANCE

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
        private const val FULL_TOLERANCE = 1e-4f
    }
}

/**
 * What the user did in the crop and rotate screen, in the order it is applied to the photo (EXIF
 * orientation already applied): rotate by [quarterTurns] clockwise, then mirror, then rotate the
 * result by [straightenDeg] (clockwise, around the center, canvas size unchanged), then cut [crop],
 * which is expressed in fractions of that canvas.
 */
data class CropRotateSpec(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straightenDeg: Float = 0f,
    val crop: CropRect = CropRect.FULL
) {
    /** Number of quarter turns normalised to 0..3. */
    val turns: Int get() = ((quarterTurns % TURNS) + TURNS) % TURNS

    /** True when only the orientation changes: no crop and no straighten. */
    val isOrientationOnly: Boolean get() = straightenDeg == 0f && crop.isFull

    /** True when applying the spec changes nothing. */
    val isIdentity: Boolean get() = isOrientationOnly && turns == 0 && !flipH && !flipV

    private companion object {
        const val TURNS = 4
    }
}
