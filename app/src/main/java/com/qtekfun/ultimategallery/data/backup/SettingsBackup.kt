package com.qtekfun.ultimategallery.data.backup

import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.data.profile.ProfileNaming
import com.qtekfun.ultimategallery.data.profile.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** A finished export: the [json] document, how many profiles it holds and how many lost an image watermark. */
data class BackupExport(val json: String, val profiles: Int, val profilesWithoutImage: Int)

/** What importing a backup changed. */
data class RestoreResult(val settingsApplied: Boolean, val profilesAdded: Int, val profilesSkipped: Int)

/**
 * Exports the app's configuration (preferences and watermark profiles) and imports it back. Nothing
 * here touches photos, folders or any other media.
 */
@Singleton
class SettingsBackup @Inject constructor(private val settings: SettingsRepository, private val profiles: ProfileRepository) {
    suspend fun export(appVersion: String): BackupExport {
        val all = profiles.profiles.first()
        val json = BackupCodec.encode(settings.settings.first(), all, appVersion)
        return BackupExport(json, all.size, all.count { BackupCodec.hasImage(it.source) })
    }

    /**
     * Replaces the preferences and adds the profiles. Existing profiles are never changed or deleted:
     * one that is already present with the same content is skipped (so importing twice adds nothing),
     * and one that only shares a name is added under a free "name imported" name.
     */
    suspend fun restore(contents: BackupCodec.Contents): RestoreResult {
        contents.settings?.let { settings.restore(it) }
        val existing = profiles.profiles.first()
        val names = existing.map { it.name }.toMutableList()
        val known = existing.map(BackupCodec::portable)
        var added = 0
        var skipped = 0
        for (profile in contents.profiles) {
            if (known.any { it == profile }) {
                skipped++
                continue
            }
            val taken = names.any { it.equals(profile.name, ignoreCase = true) }
            val name = if (taken) ProfileNaming.copyName(profile.name, names, IMPORTED_SUFFIX) else profile.name
            profiles.saveAs(profile, name)
            names += name
            added++
        }
        return RestoreResult(contents.settings != null, added, skipped)
    }

    private companion object {
        const val IMPORTED_SUFFIX = "imported"
    }
}
