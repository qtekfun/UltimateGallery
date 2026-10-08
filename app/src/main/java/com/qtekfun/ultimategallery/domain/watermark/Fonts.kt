package com.qtekfun.ultimategallery.domain.watermark

import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/** Identifiers of the fonts a text watermark can use. They are stored in profiles, so they must stay stable. */
object FontIds {
    /** The system default font. */
    const val DEFAULT = "default"

    const val BUNDLED_PREFIX = "bundled:"
    const val IMPORTED_PREFIX = "imported:"

    fun bundled(name: String) = BUNDLED_PREFIX + name

    fun imported(fileName: String) = IMPORTED_PREFIX + fileName
}

enum class FontKind { DEFAULT, BUNDLED, IMPORTED }

/** A selectable font. [label] is what the user sees (the file name without extension for imported fonts). */
data class FontInfo(val id: String, val label: String, val kind: FontKind)

sealed interface FontImportResult {
    data class Imported(val font: FontInfo) : FontImportResult

    /** The same font file was imported before. */
    data class AlreadyPresent(val font: FontInfo) : FontImportResult

    /** The file is not a usable TrueType/OpenType font. */
    data object Invalid : FontImportResult
}

/**
 * The fonts available to watermarks: the system default, the free fonts shipped inside the app (all
 * under the SIL Open Font License) and the fonts the user imported. Imported fonts are copied into
 * app-private storage so profiles keep working when the original file moves.
 */
interface FontLibrary {
    /** Every selectable font, default first, then bundled, then imported; updates after an import or removal. */
    val fonts: Flow<List<FontInfo>>

    /** The typeface for [id], or null when it is unknown or its file is gone (callers fall back to the default). */
    fun typeface(id: String): Typeface?

    suspend fun import(uri: Uri): FontImportResult

    /** Removes an imported font; bundled fonts and the default cannot be removed. Profiles that used it fall back to the default font. */
    suspend fun remove(id: String)
}
