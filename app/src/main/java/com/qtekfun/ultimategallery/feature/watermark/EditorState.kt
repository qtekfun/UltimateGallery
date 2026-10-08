package com.qtekfun.ultimategallery.feature.watermark

import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.watermark.Orientation
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile

enum class EditorTab { MARK, STYLE, PLACEMENT, EXPORT }

enum class MarkType { TEXT, IMAGE, TILED }

data class EditorUiState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val profile: WatermarkProfile = WatermarkProfile(),
    val snapEnabled: Boolean = true,
    val guideX: Float? = null,
    val guideY: Float? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tab: EditorTab = EditorTab.MARK,
    val message: EditorMessage? = null,
    val profiles: List<WatermarkProfile> = emptyList(),
    /** The stored version of the loaded profile, to tell whether there are unsaved changes. */
    val savedProfile: WatermarkProfile? = null
) {
    val dirty: Boolean get() = savedProfile != null && savedProfile != profile

    val current: MediaItem? get() = items.getOrNull(index)

    val orientation: Orientation
        get() = current?.let { Orientation.of(it.width, it.height) } ?: Orientation.LANDSCAPE

    val placement: Placement get() = profile.placementFor(orientation)
}

enum class EditorMessage { LOGO_IMPORT_FAILED, EMPTY_SELECTION, PROFILE_SAVED, LAST_PROFILE, FONT_IMPORTED, FONT_ALREADY_PRESENT, FONT_INVALID }
