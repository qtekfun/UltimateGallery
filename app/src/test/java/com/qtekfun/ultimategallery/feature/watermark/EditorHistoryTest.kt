package com.qtekfun.ultimategallery.feature.watermark

import com.qtekfun.ultimategallery.domain.watermark.ExportFormat
import com.qtekfun.ultimategallery.domain.watermark.ExportSettings
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    private fun p(opacity: Float) = WatermarkProfile(opacity = opacity)

    @Test
    fun undoAndRedoWalkTheSnapshots() {
        val h = EditorHistory()
        h.record(p(0.1f), null, 0)
        h.record(p(0.2f), null, 1)
        assertTrue(h.canUndo)
        assertEquals(0.2f, h.undo(p(0.3f))!!.opacity, 0f)
        assertEquals(0.1f, h.undo(p(0.2f))!!.opacity, 0f)
        assertFalse(h.canUndo)
        assertEquals(0.2f, h.redo(p(0.1f))!!.opacity, 0f)
        assertEquals(0.3f, h.redo(p(0.2f))!!.opacity, 0f)
        assertNull(h.redo(p(0.3f)))
    }

    @Test
    fun aNewEditClearsTheRedoStack() {
        val h = EditorHistory()
        h.record(p(0.1f), null, 0)
        h.undo(p(0.2f))
        assertTrue(h.canRedo)
        h.record(p(0.1f), null, 1)
        assertFalse(h.canRedo)
    }

    @Test
    fun editsWithTheSameKeyInQuickSuccessionShareOneStep() {
        val h = EditorHistory()
        h.record(p(0.1f), "opacity", 0)
        h.record(p(0.2f), "opacity", 100)
        h.record(p(0.3f), "opacity", 200)
        assertEquals(0.1f, h.undo(p(0.4f))!!.opacity, 0f)
        assertFalse(h.canUndo)
    }

    @Test
    fun aPauseOrADifferentKeyStartsANewStep() {
        val h = EditorHistory()
        h.record(p(0.1f), "opacity", 0)
        h.record(p(0.2f), "opacity", EditorHistory.COALESCE_MS + 1)
        h.record(p(0.3f), "text", EditorHistory.COALESCE_MS + 2)
        assertEquals(0.3f, h.undo(p(0.4f))!!.opacity, 0f)
        assertEquals(0.2f, h.undo(p(0.3f))!!.opacity, 0f)
        assertEquals(0.1f, h.undo(p(0.2f))!!.opacity, 0f)
    }

    @Test
    fun theStackIsBounded() {
        val h = EditorHistory(limit = 3)
        repeat(10) { h.record(p(it / 10f), null, it.toLong()) }
        var steps = 0
        var current = p(1f)
        while (true) current = h.undo(current)?.also { steps++ } ?: break
        assertEquals(3, steps)
    }

    @Test
    fun undoKeepsTheNameIdAndExportSettings() {
        val h = EditorHistory()
        h.record(WatermarkProfile(id = 1, name = "old", opacity = 0.1f), null, 0)
        val current = WatermarkProfile(
            id = 9,
            name = "new",
            opacity = 0.5f,
            export = ExportSettings(format = ExportFormat.PNG)
        )
        val restored = h.undo(current)!!
        assertEquals(0.1f, restored.opacity, 0f)
        assertEquals(9L, restored.id)
        assertEquals("new", restored.name)
        assertEquals(ExportFormat.PNG, restored.export.format)
    }
}
