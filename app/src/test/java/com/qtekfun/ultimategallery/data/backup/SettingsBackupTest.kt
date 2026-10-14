package com.qtekfun.ultimategallery.data.backup

import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.data.prefs.ThemeMode
import com.qtekfun.ultimategallery.data.profile.ProfileRepository
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.testing.TestStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsBackupTest {
    @get:Rule val stores = TestStores()

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var backup: SettingsBackup

    @Before
    fun setUp() {
        db = stores.database()
        settings = stores.settings()
        profiles = ProfileRepository(db.profileDao(), settings)
        backup = SettingsBackup(settings, profiles)
    }

    private suspend fun importFrom(json: String) = backup.restore(BackupCodec.decode(json)!!)

    @Test
    fun exportThenImportRestoresSettingsAndAddsProfiles() = runTest(stores.dispatcher) {
        settings.setThemeMode(ThemeMode.DARK)
        settings.setPhotoGridColumns(6)
        settings.setAutoHideApp("whatsapp", true)
        profiles.save(WatermarkProfile(name = "Shop", source = WatermarkSource.Text("SOLD")))
        val export = backup.export("1.0.0")
        assertEquals(1, export.profiles)

        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setPhotoGridColumns(3)
        settings.setAutoHideApp("whatsapp", false)
        settings.setLastProfileId(42)

        val result = importFrom(export.json)
        assertEquals(RestoreResult(settingsApplied = true, profilesAdded = 0, profilesSkipped = 1), result)
        val restored = settings.settings.first()
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(6, restored.photoGridColumns)
        assertEquals(setOf("whatsapp"), restored.autoHideApps)
        assertEquals(42L, restored.lastProfileId)
    }

    @Test
    fun importingTwiceDoesNotDuplicateProfiles() = runTest(stores.dispatcher) {
        val source = BackupCodec.encode(
            settings.settings.first(),
            listOf(WatermarkProfile(name = "Shop", source = WatermarkSource.Text("A")), WatermarkProfile(name = "Other", source = WatermarkSource.Text("B"))),
            "1.0.0"
        )
        assertEquals(2, importFrom(source).profilesAdded)
        assertEquals(RestoreResult(true, 0, 2), importFrom(source))
        assertEquals(listOf("Other", "Shop"), profiles.profiles.first().map { it.name }.sorted())
    }

    @Test
    fun sameNameWithDifferentContentIsAddedUnderANewName() = runTest(stores.dispatcher) {
        profiles.save(WatermarkProfile(name = "Shop", source = WatermarkSource.Text("mine")))
        val source = BackupCodec.encode(
            settings.settings.first(),
            listOf(WatermarkProfile(name = "Shop", source = WatermarkSource.Text("theirs"))),
            "1.0.0"
        )
        assertEquals(1, importFrom(source).profilesAdded)
        val all = profiles.profiles.first()
        assertEquals(listOf("Shop", "Shop imported"), all.map { it.name }.sorted())
        assertEquals(WatermarkSource.Text("mine").text, (all.first { it.name == "Shop" }.source as WatermarkSource.Text).text)
    }
}
