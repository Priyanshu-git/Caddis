package com.nexxlabs.caddis.domain.notes

/**
 * Undo for the note editor (NOTE-01). The editor library has no history of its own, so the screen records
 * each change here and asks for the state to go back to.
 *
 * Changes that follow each other within [groupMillis] are one step, so undo takes back a burst of typing
 * rather than one letter. At most [limit] steps are kept; the oldest go first. Undoing starts a new step,
 * so the next change after an undo can itself be undone on its own.
 *
 * [same] says when two states hold the same content (a caret move alone is not a change worth a step).
 */
class EditHistory<T>(
    initial: T,
    private val groupMillis: Long = 1_000,
    private val limit: Int = 100,
    private val same: (T, T) -> Boolean = { a, b -> a == b },
) {
    private val past = ArrayDeque<T>()
    private var lastAt: Long? = null

    /** The state the editor shows now, as far as this history knows. */
    var current: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()

    /** The editor now shows [state], changed at [atMillis]. Returns true when it counted as a change. */
    fun record(state: T, atMillis: Long): Boolean {
        if (same(state, current)) return false
        val last = lastAt
        if (last == null || atMillis - last >= groupMillis) {
            past.addLast(current)
            while (past.size > limit) past.removeFirst()
        }
        current = state
        lastAt = atMillis
        return true
    }

    /**
     * The editor was set to [state] by something that is not an edit (it was loaded, or an undo was applied):
     * it becomes [current] without adding a step.
     */
    fun replace(state: T) {
        current = state
        lastAt = null
    }

    /** Steps back once: the state to show, or null when there is nothing to undo. */
    fun undo(): T? {
        val previous = past.removeLastOrNull() ?: return null
        current = previous
        lastAt = null
        return previous
    }
}
