package com.qtekfun.ultimategallery.data.profile

import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource

/**
 * Early pre-releases shipped a factory profile, watermark text and export folder named after a third-party
 * service. Profiles stored by those builds keep the old values, so they are renamed to the current neutral
 * defaults the first time they are loaded. The old name is kept as code points so it does not appear in the sources.
 */
internal object LegacyDefaults {
    private val OLD_NAME = String(intArrayOf(87, 97, 108, 108, 97, 112, 111, 112), 0, 8)

    const val NEW_NAME = "Default"
    const val NEW_TEXT = "UltimateGallery"
    const val NEW_FOLDER = "UltimateGallery"

    fun migrate(profile: WatermarkProfile): WatermarkProfile = profile.copy(
        name = if (profile.name.trim().equals(OLD_NAME, ignoreCase = true)) NEW_NAME else profile.name,
        source = migrate(profile.source),
        export = profile.export.copy(destination = profile.export.destination.replace(OLD_NAME, NEW_FOLDER, ignoreCase = true))
    )

    private fun migrate(source: WatermarkSource): WatermarkSource = when (source) {
        is WatermarkSource.Text ->
            if (source.text.trim().removePrefix("@").equals(OLD_NAME, ignoreCase = true)) source.copy(text = NEW_TEXT) else source
        is WatermarkSource.Tiled -> source.copy(base = migrate(source.base))
        is WatermarkSource.Image -> source
    }
}
