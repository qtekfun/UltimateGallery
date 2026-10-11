package com.qtekfun.ultimategallery.data.profile

import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.watermark.Placement
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.testing.TestStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileRepositoryTest {
    @get:Rule val stores = TestStores()

    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        db = stores.database()
        settings = stores.settings()
        repo = ProfileRepository(db.profileDao(), settings)
    }

    @Test
    fun firstRunCreatesTheDefaultDefaultProfile() = runTest(stores.dispatcher) {
        repo.ensureDefault()
        val all = repo.profiles.first()
        assertEquals(listOf("Default"), all.map { it.name })
        repo.ensureDefault()
        assertEquals(1, repo.profiles.first().size)
    }

    @Test
    fun saveRoundTripsEveryPartOfTheProfile() = runTest(stores.dispatcher) {
        val original = WatermarkProfile(
            name = "Shop",
            source = WatermarkSource.Tiled(WatermarkSource.Text("SOLD"), spacing = 1.2f),
            opacity = 0.4f,
            portrait = Placement(0.1f, 0.2f, 0.3f, 15f),
            landscape = Placement(0.7f, 0.8f, 0.2f, -5f)
        )
        val saved = repo.save(original)
        assertTrue(saved.id > 0)
        assertEquals(saved, repo.get(saved.id))
        assertEquals(original.copy(id = saved.id), repo.get(saved.id))
    }

    @Test
    fun savingAnExistingProfileUpdatesIt() = runTest(stores.dispatcher) {
        val saved = repo.save(WatermarkProfile(name = "A"))
        repo.save(saved.copy(opacity = 0.1f))
        val all = repo.profiles.first()
        assertEquals(1, all.size)
        assertEquals(0.1f, all.single().opacity, 0f)
    }

    @Test
    fun renameDuplicateAndSaveAs() = runTest(stores.dispatcher) {
        val a = repo.save(WatermarkProfile(name = "A"))
        repo.rename(a.id, "  Renamed  ")
        assertEquals("Renamed", repo.get(a.id)!!.name)
        val copy = repo.duplicate(a.id)!!
        assertEquals("Renamed copy", copy.name)
        assertEquals("Renamed copy 2", repo.duplicate(a.id)!!.name)
        val other = repo.saveAs(a, "Other")
        assertEquals(4, repo.profiles.first().size)
        assertTrue(other.id != a.id)
    }

    @Test
    fun theLastProfileCannotBeDeleted() = runTest(stores.dispatcher) {
        val a = repo.save(WatermarkProfile(name = "A"))
        assertFalse(repo.delete(a.id))
        val b = repo.save(WatermarkProfile(name = "B"))
        assertTrue(repo.delete(a.id))
        assertEquals(listOf(b.id), repo.profiles.first().map { it.id })
        assertNull(repo.get(a.id))
    }

    @Test
    fun theLastUsedProfileIsPreselected() = runTest(stores.dispatcher) {
        val a = repo.save(WatermarkProfile(name = "A"))
        val b = repo.save(WatermarkProfile(name = "B"))
        repo.markUsed(a.id)
        assertEquals(a.id, repo.lastUsed().id)
        repo.markUsed(b.id)
        assertEquals(b.id, repo.lastUsed().id)
        repo.delete(b.id)
        // Falls back to a remaining profile once the remembered one is gone.
        assertEquals(a.id, repo.lastUsed().id)
    }

    // The old factory name, as code points so it does not appear in the sources.
    private val old = String(intArrayOf(87, 97, 108, 108, 97, 112, 111, 112), 0, 8)

    @Test
    fun profilesStoredByEarlyBuildsAreRenamedToTheNeutralDefaults() = runTest(stores.dispatcher) {
        val legacy = WatermarkProfile(
            name = old,
            source = WatermarkSource.Text("@" + old.lowercase()),
            export = com.qtekfun.ultimategallery.domain.watermark.ExportSettings(destination = "Pictures/$old")
        )
        val saved = repo.save(legacy)
        repo.markUsed(saved.id)
        val loaded = repo.lastUsed()
        assertEquals("Default", loaded.name)
        assertEquals(WatermarkSource.Text("UltimateGallery", (loaded.source as WatermarkSource.Text).style), loaded.source)
        assertEquals("Pictures/UltimateGallery", loaded.export.destination)
        assertEquals(saved.id, loaded.id)
        // Stored, not just returned.
        assertEquals("Default", repo.get(saved.id)!!.name)
    }

    @Test
    fun customProfilesAreLeftAlone() = runTest(stores.dispatcher) {
        val custom = repo.save(WatermarkProfile(name = "My shop", source = WatermarkSource.Text("@myshop")))
        repo.markUsed(custom.id)
        assertEquals(custom, repo.lastUsed())
    }
}
