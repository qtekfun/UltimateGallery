package com.qtekfun.ultimategallery.data.folders

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.domain.Folder
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceHidingRepositoryTest {
    private class FakeStorage(val dirs: Map<Long, File>) : DeviceStorage {
        var access = true
        val scanned = mutableListOf<File>()

        override fun hasAllFilesAccess() = access

        override suspend fun directoryOf(bucketId: Long) = dirs[bucketId]

        override suspend fun rescan(dir: File) {
            scanned += dir
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var trips: File
    private lateinit var storage: FakeStorage
    private lateinit var repo: DeviceHidingRepository

    private val folder = Folder(7, "Trips", "Pictures/Trips/", 3, Uri.EMPTY, false, 0)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        root = Files.createTempDirectory("volume").toFile()
        trips = File(root, "Pictures/Trips").apply { mkdirs() }
        File(trips, "photo.jpg").writeText("not really a photo")
        storage = FakeStorage(mapOf(7L to trips))
        repo = DeviceHidingRepository(db.deviceHiddenFolderDao(), storage)
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun hidingWritesAnEmptyNomediaAndRemembersTheFolder() = runBlocking {
        assertEquals(DeviceHideResult.DONE, repo.hide(folder, now = 42))
        val marker = File(trips, ".nomedia")
        assertTrue(marker.isFile)
        assertEquals(0L, marker.length())
        assertEquals("not really a photo", File(trips, "photo.jpg").readText())
        val row = repo.observe().first().single()
        assertEquals(trips.path, row.path)
        assertEquals("Pictures/Trips/", row.relativePath)
        assertEquals(listOf(trips), storage.scanned)
    }

    @Test
    fun showingDeletesOnlyTheMarker() = runBlocking {
        repo.hide(folder)
        assertEquals(DeviceHideResult.DONE, repo.show(trips.path))
        assertFalse(File(trips, ".nomedia").exists())
        assertTrue(File(trips, "photo.jpg").exists())
        assertTrue(repo.observe().first().isEmpty())
    }

    @Test
    fun anExistingNomediaIsKept() = runBlocking {
        File(trips, ".nomedia").writeText("")
        assertEquals(DeviceHideResult.DONE, repo.hide(folder))
        assertEquals(1, repo.observe().first().size)
    }

    @Test
    fun nothingHappensWithoutAccess() = runBlocking {
        storage.access = false
        assertEquals(DeviceHideResult.NO_ACCESS, repo.hide(folder))
        assertFalse(File(trips, ".nomedia").exists())
        assertTrue(repo.observe().first().isEmpty())
    }

    @Test
    fun standardTopLevelFoldersAreRefused() = runBlocking {
        assertEquals(DeviceHideResult.NOT_ALLOWED, repo.hide(folder.copy(relativePath = "DCIM/")))
    }

    @Test
    fun anUnknownFolderIsReported() = runBlocking {
        assertEquals(DeviceHideResult.NOT_FOUND, repo.hide(folder.copy(bucketId = 99)))
    }

    @Test
    fun aFolderThatWasDeletedIsForgottenOnShow() = runBlocking {
        repo.hide(folder)
        trips.deleteRecursively()
        assertEquals(DeviceHideResult.DONE, repo.show(trips.path))
        assertTrue(repo.observe().first().isEmpty())
    }
}
