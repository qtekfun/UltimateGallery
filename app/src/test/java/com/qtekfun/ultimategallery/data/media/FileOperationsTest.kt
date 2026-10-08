package com.qtekfun.ultimategallery.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class FileOperationsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FakeFileStore
    private val consent = FakeConsent()
    private lateinit var ops: FileOperations

    @Before
    fun setUp() {
        store = FakeFileStore(tmp.newFolder())
        ops = FileOperations(store, consent, Dispatchers.Unconfined)
    }

    @Test
    fun trashAsksConsentOnceAndReportsAllItems() = runBlocking {
        val a = store.add(1, "a.jpg", "DCIM/Camera/")
        val b = store.add(2, "b.jpg", "DCIM/Camera/")
        assertEquals(OpResult.Done(2, 0), ops.trash(listOf(a, b)))
        assertEquals(1, consent.requests)
        assertEquals(true, store.lastTrashed)
        assertEquals(OpResult.Done(2, 0), ops.restore(listOf(a, b)))
        assertEquals(false, store.lastTrashed)
    }

    @Test
    fun deniedConsentLeavesEverythingAlone() = runBlocking {
        consent.allow = false
        val a = store.add(1, "a.jpg", "DCIM/Camera/")
        assertEquals(OpResult.Denied, ops.trash(listOf(a)))
        assertEquals(OpResult.Denied, ops.rename(a, "new"))
        assertEquals(OpResult.Denied, ops.move(listOf(a), "Pictures/Trips"))
        assertEquals("a.jpg", store.files.getValue(1).name)
        assertEquals("DCIM/Camera/", store.files.getValue(1).path)
    }

    @Test
    fun renameKeepsTheExtension() = runBlocking {
        val a = store.add(1, "a.jpg", "DCIM/Camera/")
        assertEquals(OpResult.Done(1, 0), ops.rename(a, " holiday/1 "))
        assertEquals("holiday1.jpg", store.files.getValue(1).name)
        assertTrue(ops.rename(a, "  ") is OpResult.Failed)
    }

    @Test
    fun moveUpdatesThePathAndCountsFailures() = runBlocking {
        val a = store.add(1, "a.jpg", "DCIM/Camera/")
        val b = store.add(2, "b.jpg", "DCIM/Camera/")
        store.failRenameFor = setOf(2L)
        assertEquals(OpResult.Done(1, 1), ops.move(listOf(a, b), "/Pictures//Trips/"))
        assertEquals("Pictures/Trips/", store.files.getValue(1).path)
        assertEquals("DCIM/Camera/", store.files.getValue(2).path)
    }

    @Test
    fun moveRejectsInvalidDestinations() = runBlocking {
        val a = store.add(1, "a.jpg", "DCIM/Camera/")
        assertTrue(ops.move(listOf(a), "Download/Trips") is OpResult.Failed)
        assertTrue(ops.copy(listOf(a), "../Pictures") is OpResult.Failed)
        assertEquals(0, consent.requests)
    }

    @Test
    fun copyCreatesVisibleRowsWithTheSameBytesAndNeedsNoConsent() = runBlocking {
        val a = store.add(1, "a.jpg", "DCIM/Camera/", byteArrayOf(9, 8, 7))
        val b = store.add(2, "b.jpg", "DCIM/Camera/")
        store.failOpenFor = setOf(2L)
        assertEquals(OpResult.Done(1, 1), ops.copy(listOf(a, b), "Pictures/Trips"))
        assertEquals(0, consent.requests)
        val copy = store.files.values.single { it.path == "Pictures/Trips/" }
        assertEquals("a.jpg", copy.name)
        assertFalse(copy.pending)
        assertEquals(listOf<Byte>(9, 8, 7), copy.bytes.toList())
        // The failed item must not leave a hidden row behind.
        assertEquals(3, store.files.size)
    }

    @Test
    fun unsupportedTypesAreNotStripped() = runBlocking {
        store.add(1, "a.gif", "DCIM/Camera/")
        val gif = store.item(1, "a.gif", "DCIM/Camera/", mime = "image/gif")
        assertNull(ops.saveCopyWithoutMetadata(gif))
        assertNull(ops.strippedCopyForSharing(gif))
    }
}
