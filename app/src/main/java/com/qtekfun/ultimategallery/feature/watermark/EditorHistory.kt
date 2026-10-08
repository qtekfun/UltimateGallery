package com.qtekfun.ultimategallery.feature.watermark

import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile

/**
 * Bounded undo/redo of profile snapshots. Export settings, name and id are not part of the history:
 * undoing a slider must not rename the profile or change where files go.
 */
class EditorHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<WatermarkProfile>()
    private val redoStack = ArrayDeque<WatermarkProfile>()
    private var lastKey: String? = null
    private var lastTimeMs = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Records [before] as an undo step. Consecutive edits with the same [key] within [COALESCE_MS]
     * (typing, dragging a slider) share one step. A null key always starts a new step.
     */
    fun record(before: WatermarkProfile, key: String?, nowMs: Long) {
        val merge = key != null && key == lastKey && nowMs - lastTimeMs < COALESCE_MS
        lastKey = key
        lastTimeMs = nowMs
        if (merge) return
        undoStack.addLast(before)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Ends coalescing, so the next edit starts a new step. */
    fun breakCoalescing() {
        lastKey = null
    }

    fun undo(current: WatermarkProfile): WatermarkProfile? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastKey = null
        return previous.keepingNonHistoryFrom(current)
    }

    fun redo(current: WatermarkProfile): WatermarkProfile? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastKey = null
        return next.keepingNonHistoryFrom(current)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastKey = null
    }

    private fun WatermarkProfile.keepingNonHistoryFrom(current: WatermarkProfile) = copy(id = current.id, name = current.name, export = current.export)

    companion object {
        const val DEFAULT_LIMIT = 60
        const val COALESCE_MS = 800L
    }
}
