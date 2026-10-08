package com.qtekfun.ultimategallery.feature.watermark

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.data.profile.ProfileCodec
import com.qtekfun.ultimategallery.data.profile.ProfileRepository
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportStatus
import com.qtekfun.ultimategallery.domain.watermark.MarkFont
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorStyleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var db: AppDatabase
    private lateinit var settingsFile: File
    private lateinit var repo: ProfileRepository
    private lateinit var vm: WatermarkEditorViewModel
    private val exported = mutableListOf<WatermarkProfile>()

    private val item = MediaItem(1, Uri.parse("content://media/1"), "a.jpg", "image/jpeg", false, 0, 400, 300, 1, 0, 1, null)

    private val media = object : MediaRepository {
        override fun observeFolders(): Flow<List<Folder>> = emptyFlow()
        override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = emptyFlow()
        override suspend fun loadItems(ids: List<Long>): List<MediaItem> = listOf(item)
    }

    private val exporter = object : ExportController {
        override fun start(itemIds: List<Long>, profile: WatermarkProfile): String {
            exported += profile
            return "job"
        }

        override fun observe(jobId: String): Flow<ExportStatus> = emptyFlow()

        override fun cancel(jobId: String) = Unit
    }

    private val styleA = TextStyleSpec(font = MarkFont.SERIF, weight = 900, italic = true, colorArgb = 0xFF336699.toInt(), outlineEnabled = true)
    private val styleB = TextStyleSpec(font = MarkFont.MONOSPACE, weight = 200, backgroundEnabled = true, shadowEnabled = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        settingsFile = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
        repo = ProfileRepository(db.profileDao(), SettingsRepository(PreferenceDataStoreFactory.create { settingsFile }))
        val batch = BatchSelection().apply { set(listOf(1L)) }
        vm = WatermarkEditorViewModel(SavedStateHandle(), media, batch, LogoImporter(context), exporter, repo, WatermarkRenderer { null })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        settingsFile.delete()
    }

    /** Runs queued coroutines until [done] holds; Room does its IO on its own threads, so poll briefly. */
    private fun settle(done: () -> Boolean = { vm.state.value.savedProfile != null && !vm.state.value.loading }) {
        repeat(WAIT_ROUNDS) {
            scope.testScheduler.advanceUntilIdle()
            if (done()) return
            Thread.sleep(POLL_MS)
        }
        assertTrue("view model did not settle", done())
    }

    private fun style() = (vm.state.value.profile.source as WatermarkSource.Text).style

    @Test
    fun styleEditedBeforeTheInitialProfileLoadIsNotOverwritten() {
        // The profile is loaded asynchronously; the user may already be tapping chips by then.
        vm.setTextStyle(styleA)
        settle()
        assertEquals(styleA, style())
        assertTrue(vm.state.value.dirty)
        assertNotEquals0(vm.state.value.profile.id)
    }

    @Test
    fun styleEditedAfterTheLoadIsKept() {
        settle()
        vm.setTextStyle(styleA)
        vm.setTextStyle(styleB)
        assertEquals(styleB, style())
        vm.undo()
        assertEquals(WatermarkSource.Text("@wallapop").style, style())
        vm.redo()
        assertEquals(styleB, style())
    }

    @Test
    fun styleSurvivesTheJobFileOnTheWayToExport() {
        settle()
        vm.setTextStyle(styleA)
        assertNotNull(vm.startExport())
        val job = exported.single()
        assertEquals(styleA, (job.source as WatermarkSource.Text).style)
        // The worker only ever sees the profile after a trip through ProfileCodec.
        assertEquals(job, ProfileCodec.fromJson(ProfileCodec.toJson(job)))
    }

    @Test
    fun loadedProfileStyleIsKeptWhenSwitchingMarkType() {
        settle()
        val stored = kotlinx.coroutines.runBlocking {
            repo.saveAs(WatermarkProfile(source = WatermarkSource.Text("Shop", styleA)), "Styled")
        }
        vm.selectProfile(stored.id)
        settle { (vm.state.value.profile.source as? WatermarkSource.Text)?.style == styleA }
        vm.setType(MarkType.TILED)
        vm.setType(MarkType.TEXT)
        assertEquals(WatermarkSource.Text("Shop", styleA), vm.state.value.profile.source)
    }

    private fun assertNotEquals0(id: Long) = assertTrue("profile id should come from the store", id != 0L)

    private companion object {
        const val WAIT_ROUNDS = 200
        const val POLL_MS = 10L
    }
}
