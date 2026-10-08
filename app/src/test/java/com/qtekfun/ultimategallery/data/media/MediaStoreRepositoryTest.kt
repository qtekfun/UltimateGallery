package com.qtekfun.ultimategallery.data.media

import android.content.Context
import android.os.Looper
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = MediaStoreRepository(context, Dispatchers.IO)

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, MediaStore.AUTHORITY)
        FakeMediaProvider.rows = listOf(
            FakeRow(1, "a.jpg", bucketId = 10, bucketName = "Camera", path = "DCIM/Camera/", dateTaken = 3_000),
            FakeRow(2, "b.jpg", bucketId = 10, bucketName = "Camera", path = "DCIM/Camera/", dateTaken = 5_000),
            FakeRow(
                3,
                "c.mp4",
                isVideo = true,
                bucketId = 10,
                bucketName = "Camera",
                path = "DCIM/Camera/",
                dateTaken = 4_000
            ),
            FakeRow(4, "d.png", bucketId = 20, bucketName = null, path = "Pictures/Wallapop/", dateAdded = 9),
            FakeRow(
                5, "e.jpg", bucketId = 20, bucketName = "Wallapop", path = "Pictures/Wallapop/",
                dateTaken = 100, width = 4000, height = 3000, orientation = 90
            )
        )
    }

    @After
    fun tearDown() {
        FakeMediaProvider.rows = emptyList()
    }

    @Test
    fun foldersAreGroupedCountedAndSortedNewestFirst() = runBlocking {
        val folders = repo.observeFolders().first()
        // Folder 20's newest item is 9000 ms (date added fallback); the camera folder's is 5000 ms.
        assertEquals(listOf(20L, 10L), folders.map { it.bucketId })
        val camera = folders.single { it.bucketId == 10L }
        assertEquals(3, camera.count)
        assertEquals("Camera", camera.name)
        assertTrue(camera.coverUri.toString().endsWith("/2"))
        assertFalse(camera.coverIsVideo)
    }

    @Test
    fun folderNameFallsBackToThePathAndNewestIsTheCover() = runBlocking {
        val wallapop = repo.observeFolders().first().single { it.bucketId == 20L }
        assertEquals("Wallapop", wallapop.name)
        assertEquals(9_000L, wallapop.newestDateMs)
        assertTrue(wallapop.coverUri.toString().endsWith("/4"))
    }

    @Test
    fun itemsAreNewestFirstAndVideosKeepTheirType() = runBlocking {
        val items = repo.observeItems(10).first()
        assertEquals(listOf(2L, 3L, 1L), items.map { it.id })
        assertTrue(items[1].isVideo)
        assertTrue(items[1].uri.toString().contains("/video/"))
        assertEquals(5000L, items[1].durationMs)
    }

    @Test
    fun dateAddedIsUsedWhenDateTakenIsMissing() = runBlocking {
        assertEquals(9_000L, repo.observeItems(20).first().single { it.id == 4L }.dateMs)
    }

    @Test
    fun displayedSizeAppliesTheStoredOrientation() = runBlocking {
        val rotated = repo.observeItems(20).first().single { it.id == 5L }
        assertEquals(3000, rotated.width)
        assertEquals(4000, rotated.height)
        val plain = repo.observeItems(10).first().first()
        assertEquals(4000, plain.width)
    }

    @Test
    fun loadItemsKeepsTheRequestedOrderAndSkipsMissingIds() = runBlocking {
        assertEquals(listOf(5L, 1L), repo.loadItems(listOf(5, 99, 1)).map { it.id })
        assertTrue(repo.loadItems(emptyList()).isEmpty())
    }

    @Test
    fun foldersRefreshWhenTheMediaStoreChanges() = runBlocking {
        repo.observeFolders().test {
            assertEquals(2, awaitItem().size)
            FakeMediaProvider.rows = FakeMediaProvider.rows +
                FakeRow(6, "f.jpg", bucketId = 30, bucketName = "New", path = "Pictures/New/", dateTaken = 1)
            context.contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
