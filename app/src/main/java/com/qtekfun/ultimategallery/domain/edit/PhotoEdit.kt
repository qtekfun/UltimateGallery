package com.qtekfun.ultimategallery.domain.edit

/** A point in fractions (0..1) of the image, origin at the top left. Points of a stroke may fall slightly outside. */
data class EditPoint(val x: Float, val y: Float)

/**
 * Light and color settings. Every value is 0 when untouched. [brightness], [contrast], [exposure], [saturation] and [warmth]
 * go from -1 to 1; [sharpness] and [vignette] from 0 to 1.
 */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val exposure: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f
) {
    val isIdentity: Boolean get() = this == NONE

    /** True when only the color matrix is affected (no sharpening and no vignette). */
    val isColorOnly: Boolean get() = sharpness == 0f && vignette == 0f

    fun get(kind: AdjustKind): Float = when (kind) {
        AdjustKind.BRIGHTNESS -> brightness
        AdjustKind.CONTRAST -> contrast
        AdjustKind.EXPOSURE -> exposure
        AdjustKind.SATURATION -> saturation
        AdjustKind.WARMTH -> warmth
        AdjustKind.SHARPNESS -> sharpness
        AdjustKind.VIGNETTE -> vignette
    }

    /** A copy with [kind] set to [value], clamped to the range of that kind. */
    fun with(kind: AdjustKind, value: Float): Adjustments {
        val v = value.coerceIn(kind.min, 1f)
        return when (kind) {
            AdjustKind.BRIGHTNESS -> copy(brightness = v)
            AdjustKind.CONTRAST -> copy(contrast = v)
            AdjustKind.EXPOSURE -> copy(exposure = v)
            AdjustKind.SATURATION -> copy(saturation = v)
            AdjustKind.WARMTH -> copy(warmth = v)
            AdjustKind.SHARPNESS -> copy(sharpness = v)
            AdjustKind.VIGNETTE -> copy(vignette = v)
        }
    }

    companion object {
        val NONE = Adjustments()
    }
}

enum class AdjustKind(val min: Float) {
    BRIGHTNESS(-1f),
    CONTRAST(-1f),
    EXPOSURE(-1f),
    SATURATION(-1f),
    WARMTH(-1f),
    SHARPNESS(0f),
    VIGNETTE(0f)
}

enum class FilterPreset { NONE, MONO, SEPIA, WARM, COOL, VIVID, FADE, NOIR }

/** A filter preset blended with the unfiltered photo by [intensity] (0..1). */
data class FilterChoice(val preset: FilterPreset = FilterPreset.NONE, val intensity: Float = 1f) {
    val isIdentity: Boolean get() = preset == FilterPreset.NONE || intensity <= 0f
}

enum class StrokeStyle { PEN, HIGHLIGHTER }

enum class ShapeKind { LINE, ARROW, RECTANGLE, ELLIPSE }

/** Something drawn on top of the photo. [id] is unique within a [PhotoEdit] and stable across edits. */
sealed interface Annotation {
    val id: Long
    val color: Int

    /** A freehand line through [points]; [width] is a fraction of the image's short side. */
    data class Stroke(override val id: Long, val points: List<EditPoint>, override val color: Int, val width: Float, val style: StrokeStyle) : Annotation

    /** A shape spanning [start] to [end]; rectangles and ellipses fill that box when [filled]. */
    data class Shape(
        override val id: Long,
        val kind: ShapeKind,
        val start: EditPoint,
        val end: EditPoint,
        override val color: Int,
        val width: Float,
        val filled: Boolean
    ) : Annotation

    /**
     * Text centered on [center]. [size] is the line height as a fraction of the image's short side; [rotationDeg] turns it
     * clockwise around its center. With [boxed], the text sits on a rounded box of a contrasting color.
     */
    data class Text(
        override val id: Long,
        val text: String,
        val center: EditPoint,
        val size: Float,
        val rotationDeg: Float,
        override val color: Int,
        val fontId: String,
        val boxed: Boolean
    ) : Annotation
}

enum class CoverMode { BLUR, PIXELATE }

/** A brush stroke that blurs or pixelates what it paints. [strength] goes from 0 to 1. */
data class Cover(val points: List<EditPoint>, val width: Float, val mode: CoverMode, val strength: Float)

/** One step that builds the background mask, applied in order. */
sealed interface MaskOp {
    /** Removes the plain background connected to the borders. [tolerance] goes from 0 to 1. */
    data class Auto(val tolerance: Float) : MaskOp

    /** Removes the region of similar color connected to [point]. */
    data class Seed(val point: EditPoint, val tolerance: Float) : MaskOp

    /** Erases (or with [restore], brings back) what the brush paints. */
    data class Brush(val points: List<EditPoint>, val width: Float, val restore: Boolean) : MaskOp
}

sealed interface BackgroundFill {
    data object Transparent : BackgroundFill

    data class Solid(val color: Int) : BackgroundFill
}

/** Background removal: the mask steps, the edge softness (0..1) and what replaces the removed part. */
data class BackgroundEdit(val ops: List<MaskOp> = emptyList(), val feather: Float = DEFAULT_FEATHER, val fill: BackgroundFill = BackgroundFill.Transparent) {
    val isIdentity: Boolean get() = ops.isEmpty()

    companion object {
        const val DEFAULT_FEATHER = 0.3f
    }
}

/** Everything the photo editor does to a photo, in the order it is rendered: color, sharpness, vignette, covers, background, annotations. */
data class PhotoEdit(
    val adjust: Adjustments = Adjustments.NONE,
    val filter: FilterChoice = FilterChoice(),
    val covers: List<Cover> = emptyList(),
    val background: BackgroundEdit = BackgroundEdit(),
    val annotations: List<Annotation> = emptyList()
) {
    val isIdentity: Boolean get() = adjust.isIdentity && filter.isIdentity && covers.isEmpty() && background.isIdentity && annotations.isEmpty()

    /** True when the result has transparent pixels. */
    val hasTransparency: Boolean get() = !background.isIdentity && background.fill == BackgroundFill.Transparent

    /** The edit without its annotations: what the base layer of the preview shows. */
    val base: PhotoEdit get() = if (annotations.isEmpty()) this else copy(annotations = emptyList())

    /** An id no annotation uses yet. */
    fun nextAnnotationId(): Long = (annotations.maxOfOrNull { it.id } ?: 0L) + 1

    fun annotation(id: Long?): Annotation? = id?.let { wanted -> annotations.firstOrNull { it.id == wanted } }

    fun replaceAnnotation(updated: Annotation): PhotoEdit = copy(annotations = annotations.map { if (it.id == updated.id) updated else it })

    fun removeAnnotation(id: Long): PhotoEdit = copy(annotations = annotations.filterNot { it.id == id })
}
