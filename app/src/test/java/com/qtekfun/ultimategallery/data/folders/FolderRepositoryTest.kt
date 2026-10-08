package com.qtekfun.ultimategallery.data.folders

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderRepositoryTest {
    private fun folder(id: Long, name: String, path: String) = Folder(id, name, path, 2, Uri.EMPTY, false, id)

    private val camera = folder(1, "Camera", "DCIM/Camera/")
    private val whatsapp = folder(2, "WhatsApp Images", "Pictures/WhatsApp Images/")
    private val shots = folder(3, "Screenshots", "Pictures/Screenshots/")

    private val media = object : MediaRepository {
        override fun observeFolders(): Flow<List<Folder>> = flowOf(listOf(camera, whatsapp, shots))

        override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = flowOf(emptyList())

        override suspend fun loadItems(ids: List<Long>): List<MediaItem> = emptyList()
    }

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repo: FolderRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
        settings = SettingsRepository(PreferenceDataStoreFactory.create { file })
        repo = FolderRepository(media, db.hiddenFolderDao(), settings)
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    @Test
    fun everythingIsVisibleByDefault() = runBlocking {
        assertEquals(listOf(camera, whatsapp, shots), repo.visible().first())
    }

    @Test
    fun hidingAFolderRemovesItFromTheVisibleListOnly() = runBlocking {
        repo.hide(listOf(camera))
        assertEquals(listOf(whatsapp, shots), repo.visible().first())
        assertEquals(3, repo.visible(includeHidden = true).first().size)
        val state = repo.all().first().single { it.folder == camera }
        assertTrue(state.hiddenByUser)
        assertNull(state.hiddenByApp)
    }

    @Test
    fun undoShowsTheFolderAgain() = runBlocking {
        repo.hide(listOf(camera))
        repo.unhide(listOf(camera.bucketId))
        assertEquals(3, repo.visible().first().size)
    }

    @Test
    fun autoHidingAnAppHidesItsFolders() = runBlocking {
        settings.setAutoHideApp("whatsapp", true)
        assertEquals(listOf(camera, shots), repo.visible().first())
        val state = repo.all().first().single { it.folder == whatsapp }
        assertTrue(state.hidden)
        assertEquals("whatsapp", state.hiddenByApp?.id)
        settings.setAutoHideApp("whatsapp", false)
        assertEquals(3, repo.visible().first().size)
    }

    @Test
    fun nothingIsHiddenUntilTheUserConfirms() = runBlocking {
        assertTrue(settings.settings.first().autoHideApps.isEmpty())
        assertEquals(listOf("whatsapp", "screenshots"), repo.detectedAppFolders().first().map { it.entry.id })
        assertEquals(3, repo.visible().first().size)
    }
}
