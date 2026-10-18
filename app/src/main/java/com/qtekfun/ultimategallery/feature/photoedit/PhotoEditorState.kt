package com.qtekfun.ultimategallery.feature.photoedit

import android.graphics.Bitmap
import android.net.Uri
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.edit.AdjustKind
import com.qtekfun.ultimategallery.domain.edit.CoverMode
import com.qtekfun.ultimategallery.domain.edit.PhotoEdit
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.FontInfo

enum class EditorTool { ADJUST, FILTER, DRAW, TEXT, BLUR, BACKGROUND }

enum class DrawMode { SELECT, PEN, HIGHLIGHTER, LINE, ARROW, RECTANGLE, ELLIPSE }

enum class BackgroundMode { TAP, ERASE, RESTORE }

/** Pen and shape settings; [width] is a fraction of the image's short side. */
data class DrawSettings(val mode: DrawMode = DrawMode.PEN, val color: Int = DEFAULT_DRAW_COLOR, val width: Float = 0.012f, val filled: Boolean = false)

data class TextSettings(val color: Int = DEFAULT_TEXT_COLOR, val fontId: String = FontIds.DEFAULT, val boxed: Boolean = true)

data class CoverSettings(val mode: CoverMode = CoverMode.BLUR, val width: Float = 0.08f, val strength: Float = 0.6f)

data class BackgroundSettings(val mode: BackgroundMode = BackgroundMode.TAP, val tolerance: Float = 0.25f, val brushWidth: Float = 0.05f)

/** Everything the photo editor shows. */
data class PhotoEditorState(
    val item: MediaItem? = null,
    val loadFailed: Boolean = false,
    /** The photo decoded at preview size, untouched. */
    val preview: Bitmap? = null,
    /** [preview] with the base of [edit] (everything but the annotations) applied; null until the first render. */
    val base: Bitmap? = null,
    /** A small copy for the filter thumbnails. */
    val thumbnail: Bitmap? = null,
    val edit: PhotoEdit = PhotoEdit(),
    val tool: EditorTool = EditorTool.ADJUST,
    val adjustKind: AdjustKind = AdjustKind.BRIGHTNESS,
    val draw: DrawSettings = DrawSettings(),
    val text: TextSettings = TextSettings(),
    val cover: CoverSettings = CoverSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    val selectedId: Long? = null,
    val fonts: List<FontInfo> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val rendering: Boolean = false,
    val saving: Boolean = false,
    /** True while the "copy, overwrite or cancel" question is shown. */
    val askingBehavior: Boolean = false
) {
    val hasChanges: Boolean get() = !edit.isIdentity

    /** True when the stage moves and transforms items instead of drawing. */
    val selecting: Boolean get() = tool == EditorTool.TEXT || (tool == EditorTool.DRAW && draw.mode == DrawMode.SELECT)
}

/** One-shot results of a save. */
sealed interface PhotoEditorEvent {
    /** Saved at [uri]; [asCopy] is set when overwriting was asked but the result had to be a copy (a transparent JPEG). */
    data class Saved(val uri: Uri, val asCopy: Boolean) : PhotoEditorEvent

    data object Denied : PhotoEditorEvent

    data object Failed : PhotoEditorEvent
}

private const val DEFAULT_DRAW_COLOR = 0xFFE53935.toInt()
private const val DEFAULT_TEXT_COLOR = 0xFFFFFFFF.toInt()
