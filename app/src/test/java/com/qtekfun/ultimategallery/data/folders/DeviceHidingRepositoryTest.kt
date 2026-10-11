package com.qtekfun.ultimategallery.data.folders

import android.net.Uri
import com.qtekfun.ultimategallery.domain.Folder
import com.qtekfun.ultimategallery.testing.TestStores
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    @get:Rule val stores = TestStores()

    @get:Rule val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var trips: File
    private lateinit var storage: FakeStorage
    private lateinit var repo: DeviceHidingRepository

    private val folder = Folder(7, "Trips", "Pictures/Trips/", 3, Uri.EMPTY, false, 0)

    @Before
    fun setUp() {
        root = temp.newFolder("volume")
        trips = File(root, "Pictures/Trips").apply { mkdirs() }
        File(trips, "photo.jpg").writeText("not really a photo")
        storage = FakeStorage(mapOf(7L to trips))
        repo = DeviceHidingRepository(stores.database().deviceHiddenFolderDao(), storage)
    }

    @Test
    fun hidingWritesAnEmptyNomediaAndRemembersTheFolder() = runTest(stores.dispatcher) {
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
    fun showingDeletesOnlyTheMarker() = runTest(stores.dispatcher) {
        repo.hide(folder)
        assertEquals(DeviceHideResult.DONE, repo.show(trips.path))
        assertFalse(File(trips, ".nomedia").exists())
        assertTrue(File(trips, "photo.jpg").exists())
        assertTrue(repo.observe().first().isEmpty())
    }

    @Test
    fun anExistingNomediaIsKept() = runTest(stores.dispatcher) {
        File(trips, ".nomedia").writeText("")
        assertEquals(DeviceHideResult.DONE, repo.hide(folder))
        assertEquals(1, repo.observe().first().size)
    }

    @Test
    fun nothingHappensWithoutAccess() = runTest(stores.dispatcher) {
        storage.access = false
        assertEquals(DeviceHideResult.NO_ACCESS, repo.hide(folder))
        assertFalse(File(trips, ".nomedia").exists())
        assertTrue(repo.observe().first().isEmpty())
    }

    @Test
    fun standardTopLevelFoldersAreRefused() = runTest(stores.dispatcher) {
        assertEquals(DeviceHideResult.NOT_ALLOWED, repo.hide(folder.copy(relativePath = "DCIM/")))
    }

    @Test
    fun anUnknownFolderIsReported() = runTest(stores.dispatcher) {
        assertEquals(DeviceHideResult.NOT_FOUND, repo.hide(folder.copy(bucketId = 99)))
    }

    @Test
    fun aFolderThatWasDeletedIsForgottenOnShow() = runTest(stores.dispatcher) {
        repo.hide(folder)
        trips.deleteRecursively()
        assertEquals(DeviceHideResult.DONE, repo.show(trips.path))
        assertTrue(repo.observe().first().isEmpty())
    }
}
