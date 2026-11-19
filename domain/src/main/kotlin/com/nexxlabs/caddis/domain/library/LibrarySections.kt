package com.nexxlabs.caddis.domain.library

import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus

/**
 * LIB-01: the Library lists what is still active, newest saved first, and puts everything finished in a
 * separate Completed section below it. Finished saves keep their own look; they are not dimmed.
 */
data class LibrarySections(val active: List<Item>, val completed: List<Item>) {

    /** What the open section shows: the latest [PREVIEW] completed saves, or all of them once "Show all" is tapped. */
    fun shownCompleted(showAll: Boolean): List<Item> = if (showAll) completed else completed.take(PREVIEW)

    /** How many completed saves "Show all" would add. Zero when the preview already shows every one. */
    fun hiddenCompleted(showAll: Boolean): Int = completed.size - shownCompleted(showAll).size

    companion object {
        const val PREVIEW = 3

        /**
         * [items] arrive newest saved first and keep that order in [active]. [completed] is ordered by when each
         * save was finished, latest first, so the one you just finished is on top. Deleted saves belong to neither.
         */
        fun of(items: List<Item>): LibrarySections = LibrarySections(
            active = items.filter { it.status == ItemStatus.ACTIVE },
            completed = items.filter { it.status == ItemStatus.DONE }
                .sortedWith(compareByDescending<Item> { it.doneAt ?: it.updatedAt }.thenByDescending { it.createdAt }),
        )
    }
}
