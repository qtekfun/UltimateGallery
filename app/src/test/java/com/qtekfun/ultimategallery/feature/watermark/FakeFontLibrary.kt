package com.qtekfun.ultimategallery.feature.watermark

import android.graphics.Typeface
import android.net.Uri
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.FontImportResult
import com.qtekfun.ultimategallery.domain.watermark.FontInfo
import com.qtekfun.ultimategallery.domain.watermark.FontKind
import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [FontLibrary] whose next import outcome is set by the test. */
class FakeFontLibrary : FontLibrary {
    val initial = listOf(
        FontInfo(FontIds.DEFAULT, "Default", FontKind.DEFAULT),
        FontInfo(FontIds.bundled("Lora"), "Lora", FontKind.BUNDLED),
        FontInfo(FontIds.imported("Mine"), "Mine", FontKind.IMPORTED)
    )
    val list = MutableStateFlow(initial)
    var nextImport: FontImportResult = FontImportResult.Invalid
    val removed = mutableListOf<String>()
    val imported = mutableListOf<Uri>()

    override val fonts: Flow<List<FontInfo>> = list

    override fun typeface(id: String): Typeface? = null

    override suspend fun import(uri: Uri): FontImportResult {
        imported += uri
        return nextImport
    }

    override suspend fun remove(id: String) {
        removed += id
        list.value = list.value.filterNot { it.id == id }
    }
}
