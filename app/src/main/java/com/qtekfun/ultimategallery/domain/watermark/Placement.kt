package com.qtekfun.ultimategallery.domain.watermark

/** The orientation bucket of an image. Square images count as landscape. */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        /** The bucket of an image as displayed (EXIF orientation already applied). */
        fun of(width: Int, height: Int): Orientation = if (height > width) PORTRAIT else LANDSCAPE
    }
}

/**
 * Where a watermark sits on an image, independent of the image's resolution.
 *
 * Everything is a fraction, so the same placement lands in the same relative spot on every image of
 * an orientation: a mark at 40% of the horizontal dimension stays at 40% whatever the pixel size.
 */
data class Placement(
    /** Center of the mark, 0..1 of the image width. */
    val centerX: Float,
    /** Center of the mark, 0..1 of the image height. */
    val centerY: Float,
    /** Width of the mark as a fraction of the image width. */
    val sizeFraction: Float,
    val rotationDeg: Float
) {
    companion object {
        val DefaultPortrait = Placement(0.74f, 0.93f, 0.36f, 0f)
        val DefaultLandscape = Placement(0.83f, 0.9f, 0.26f, 0f)
    }
}
