package com.qtekfun.ultimategallery.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.prefs.SettingsRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Room and DataStore for tests, both running on [dispatcher] instead of their own threads. The test
 * decides when their work runs (`advanceUntilIdle()`, or `runTest(stores.dispatcher)` which advances on
 * its own), so results never depend on timing, nothing needs polling, and nothing still runs against a
 * store once the test is over: at the end the pending work is drained, view models are cleared and the
 * stores are closed, in that order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestStores(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val databases = mutableListOf<AppDatabase>()
    private val viewModels = mutableListOf<ViewModel>()
    private var dir: File? = null

    /** An in-memory database whose queries and invalidation run on [dispatcher]. */
    fun database(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(dispatcher)
        .build()
        .also { databases += it }

    /** Settings backed by a fresh DataStore file that is read and written on [dispatcher]. */
    fun settings(name: String = "settings"): SettingsRepository {
        val root = dir ?: Files.createTempDirectory("stores").toFile().also { dir = it }
        return SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(root, "$name.preferences_pb") })
    }

    /** Clears [viewModel] (cancels its scope) before the stores close, as leaving the screen would. */
    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    /** Runs [block] on [dispatcher] to completion, with everything it waits for, and returns its result. */
    fun <T> complete(block: suspend CoroutineScope.() -> T): T {
        val result = scope.async(block = block)
        drain()
        check(result.isCompleted) { "The block is still waiting for something that doesn't run on the test dispatcher" }
        return result.getCompleted()
    }

    /** Runs everything queued on [dispatcher], including work queued by that work. */
    fun drain() = dispatcher.scheduler.advanceUntilIdle()

    override fun finished(description: Description) {
        drain()
        viewModels.forEach { it.viewModelScope.cancel() }
        scope.cancel()
        drain()
        databases.forEach { it.close() }
        dir?.deleteRecursively()
    }
}
