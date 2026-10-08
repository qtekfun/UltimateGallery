package com.qtekfun.ultimategallery.data.profile

import com.qtekfun.ultimategallery.data.db.ProfileDao
import com.qtekfun.ultimategallery.data.db.ProfileEntity
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Stores watermark profiles (templates) and remembers which one was used last. */
@Singleton
class ProfileRepository @Inject constructor(private val dao: ProfileDao, private val settings: SettingsRepository) {
    val profiles: Flow<List<WatermarkProfile>> = dao.observeAll().map { rows -> rows.map(::toProfile) }

    /** Makes sure at least the default "Wallapop" profile exists (first run). */
    suspend fun ensureDefault() {
        if (dao.count() == 0) save(WatermarkProfile(name = DEFAULT_NAME, export = settings.settings.first().exportDefaults))
    }

    suspend fun get(id: Long): WatermarkProfile? = dao.get(id)?.let(::toProfile)

    /** The profile used last, or the most recently saved one, or a fresh default. */
    suspend fun lastUsed(): WatermarkProfile {
        ensureDefault()
        val lastId = settings.settings.first().lastProfileId
        return get(lastId) ?: dao.mostRecentlyUpdated()?.let(::toProfile) ?: save(WatermarkProfile(name = DEFAULT_NAME))
    }

    /** Inserts the profile when its id is 0, otherwise updates it. Returns the stored profile. */
    suspend fun save(profile: WatermarkProfile, now: Long = System.currentTimeMillis()): WatermarkProfile {
        val name = ProfileNaming.clean(profile.name).ifEmpty { DEFAULT_NAME }
        val json = ProfileCodec.toJson(profile.copy(id = 0, name = name))
        val existing = if (profile.id != 0L) dao.get(profile.id) else null
        return if (existing == null) {
            val id = dao.insert(ProfileEntity(name = name, json = json, createdAt = now, updatedAt = now))
            profile.copy(id = id, name = name)
        } else {
            dao.update(existing.copy(name = name, json = json, updatedAt = now))
            profile.copy(name = name)
        }
    }

    /** Stores [profile] as a new profile called [name]. */
    suspend fun saveAs(profile: WatermarkProfile, name: String): WatermarkProfile = save(profile.copy(id = 0, name = name))

    suspend fun rename(id: Long, name: String) {
        val row = dao.get(id) ?: return
        dao.update(row.copy(name = ProfileNaming.clean(name).ifEmpty { row.name }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicate(id: Long): WatermarkProfile? {
        val original = get(id) ?: return null
        val names = profiles.first().map { it.name }
        return saveAs(original, ProfileNaming.copyName(original.name, names))
    }

    /** Deletes a profile. The last remaining profile cannot be deleted; returns false then. */
    suspend fun delete(id: Long): Boolean {
        if (dao.count() <= 1) return false
        dao.delete(id)
        if (settings.settings.first().lastProfileId == id) settings.setLastProfileId(NO_PROFILE)
        return true
    }

    suspend fun markUsed(id: Long) = settings.setLastProfileId(id)

    private fun toProfile(row: ProfileEntity): WatermarkProfile = ProfileCodec.fromJson(row.json).copy(id = row.id, name = row.name)

    companion object {
        const val DEFAULT_NAME = "Wallapop"
        private const val NO_PROFILE = -1L
    }
}
