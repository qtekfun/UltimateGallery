package com.qtekfun.ultimategallery.domain.watermark

import android.net.Uri

/** How a text watermark looks. Sizes are relative to the text height so the style scales with the mark. */
data class TextStyleSpec(
    /** The font to use, see [FontIds]. */
    val fontId: String = FontIds.DEFAULT,
    /** CSS-like weight, 100..900. */
    val weight: Int = 600,
    val italic: Boolean = false,
    val colorArgb: Int = WHITE,
    val outlineEnabled: Boolean = false,
    val outlineColorArgb: Int = BLACK,
    /** Outline thickness as a fraction of the text height. */
    val outlineWidth: Float = 0.08f,
    val shadowEnabled: Boolean = true,
    val shadowColorArgb: Int = 0xAA000000.toInt(),
    /** Shadow blur radius as a fraction of the text height. */
    val shadowRadius: Float = 0.08f,
    val backgroundEnabled: Boolean = false,
    val backgroundColorArgb: Int = 0x99000000.toInt(),
    /** Padding of the background pill as a fraction of the text height. */
    val backgroundPadding: Float = 0.3f
) {
    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}

/** What is drawn. */
sealed interface WatermarkSource {
    /** An imported logo, stored in app-private storage. */
    data class Image(val uri: Uri) : WatermarkSource

    data class Text(val text: String, val style: TextStyleSpec = TextStyleSpec()) : WatermarkSource

    /**
     * A [base] mark repeated over the whole image. The anchor tile sits at the placement center and the
     * whole lattice is rotated by the placement rotation. [spacing] is the gap between tiles as a
     * fraction of the tile size; [staggerX] and [staggerY] shift alternate rows and columns as a
     * fraction of the tile pitch (0.5 gives a brick pattern).
     */
    data class Tiled(val base: WatermarkSource, val spacing: Float = 0.6f, val staggerX: Float = 0.5f, val staggerY: Float = 0f) : WatermarkSource
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp"),
    PNG("png", "image/png")
}

enum class ExifMode { KEEP, STRIP_LOCATION, STRIP_ALL }

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    /** 1..100; ignored for PNG. */
    val quality: Int = 92,
    /** Longest edge of the output in pixels; null keeps the original size. */
    val maxLongEdge: Int? = null,
    /** Tokens: `{name}` original name without extension, `{n}` position in the batch, `{date}` yyyyMMdd. */
    val fileNamePattern: String = "{name}_wm",
    /** Destination under the volume root, for example `Pictures/Trips`. */
    val destination: String = "Pictures/UltimateGallery",
    val exif: ExifMode = ExifMode.STRIP_LOCATION
)

/** A reusable watermark template. [id] is 0 until the profile is stored. */
data class WatermarkProfile(
    val id: Long = 0,
    val name: String = "Default",
    val source: WatermarkSource = WatermarkSource.Text("UltimateGallery"),
    val opacity: Float = 0.85f,
    val portrait: Placement = Placement.DefaultPortrait,
    val landscape: Placement = Placement.DefaultLandscape,
    val export: ExportSettings = ExportSettings(),
    /** Distance of the snapping margin guides from the image edges, as a fraction of the width. */
    val margin: Float = 0.03f
) {
    fun placementFor(orientation: Orientation): Placement = if (orientation == Orientation.PORTRAIT) portrait else landscape

    fun withPlacement(orientation: Orientation, placement: Placement): WatermarkProfile =
        if (orientation == Orientation.PORTRAIT) copy(portrait = placement) else copy(landscape = placement)
}
