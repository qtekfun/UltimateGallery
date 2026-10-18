package com.qtekfun.ultimategallery.domain.edit

/**
 * Bounded undo/redo of immutable snapshots. Consecutive edits with the same key within [coalesceMs] (dragging a slider) share
 * one step.
 */
class SnapshotHistory<T>(private val limit: Int = DEFAULT_LIMIT, private val coalesceMs: Long = COALESCE_MS) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var lastKey: String? = null
    private var lastTimeMs = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records [before] as an undo step. A null [key] always starts a new step. */
    fun record(before: T, key: String?, nowMs: Long) {
        val merge = key != null && key == lastKey && nowMs - lastTimeMs < coalesceMs
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

    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastKey = null
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastKey = null
        return next
    }

    companion object {
        const val DEFAULT_LIMIT = 60
        const val COALESCE_MS = 800L
    }
}
