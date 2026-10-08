package com.qtekfun.ultimategallery.feature.watermark

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import com.qtekfun.ultimategallery.data.profile.ProfileRepository
import com.qtekfun.ultimategallery.domain.BatchSelection
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.domain.MediaItem
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportStatus
import com.qtekfun.ultimategallery.domain.watermark.MarkFont
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var file: File
    private val dispatcher = StandardTestDispatcher()

    private val media = object : MediaRepository {
        override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())

        override fun observeItems(bucketId: Long): Flow<List<MediaItem>> = flowOf(emptyList())

        override suspend fun loadItems(ids: List<Long>): List<MediaItem> = emptyList()
    }
    private val exporter = object : ExportController {
        override fun start(itemIds: List<Long>, profile: WatermarkProfile) = "job"

        override fun observe(jobId: String): Flow<ExportStatus> = flowOf(ExportStatus.Queued)

        override fun cancel(jobId: String) = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        file.delete()
    }

    private fun viewModel(): WatermarkEditorViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { file })
        return WatermarkEditorViewModel(
            SavedStateHandle(),
            media,
            BatchSelection(),
            LogoImporter(context),
            exporter,
            ProfileRepository(db.profileDao(), settings),
            WatermarkRenderer { null }
        )
    }

    @Test
    fun changingTheTextStyleUpdatesTheProfile() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setTextStyle((vm.state.value.profile.source as WatermarkSource.Text).style.copy(font = MarkFont.SERIF, weight = 900, italic = true))
        advanceUntilIdle()
        val style = (vm.state.value.profile.source as WatermarkSource.Text).style
        assertEquals(MarkFont.SERIF, style.font)
        assertEquals(900, style.weight)
        assertTrue(style.italic)
        vm.setText("Hello")
        advanceUntilIdle()
        val after = vm.state.value.profile.source as WatermarkSource.Text
        assertEquals("Hello", after.text)
        assertEquals(MarkFont.SERIF, after.style.font)
    }
}
