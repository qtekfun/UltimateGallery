package com.qtekfun.ultimategallery.domain.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotHistoryTest {
    @Test
    fun undoAndRedoWalkTheSnapshots() {
        val history = SnapshotHistory<Int>()
        history.record(0, null, 0)
        history.record(1, null, 10)
        assertEquals(1, history.undo(2))
        assertEquals(0, history.undo(1))
        assertNull(history.undo(0))
        assertEquals(1, history.redo(0))
        assertTrue(history.canRedo)
    }

    @Test
    fun sameKeyWithinTheWindowSharesAStep() {
        val history = SnapshotHistory<Int>(coalesceMs = 100)
        history.record(0, "slider", 0)
        history.record(1, "slider", 50)
        history.record(2, "slider", 120)
        assertEquals(0, history.undo(3))
        assertFalse(history.canUndo)
    }

    @Test
    fun aNewEditClearsRedo() {
        val history = SnapshotHistory<Int>()
        history.record(0, null, 0)
        history.undo(1)
        history.record(0, null, 5)
        assertFalse(history.canRedo)
    }

    @Test
    fun theOldestStepsAreDroppedPastTheLimit() {
        val history = SnapshotHistory<Int>(limit = 2)
        (0 until 5).forEach { history.record(it, null, it * 1000L) }
        assertEquals(4, history.undo(5))
        assertEquals(3, history.undo(4))
        assertNull(history.undo(3))
    }
}
