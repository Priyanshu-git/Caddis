package com.nexxlabs.caddis.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Decides when the bottom bar tucks away: it hides once you have dragged the content up by [hideAfterPx]
 * (reading further down) and comes back as soon as you drag the other way, or when a drag has nothing
 * left to scroll. Jitter shorter than the threshold is ignored; a change of direction restarts the count.
 */
internal class BarVisibility(private val hideAfterPx: Float) {
    var hidden by mutableStateOf(false)
        private set

    private var travelled = 0f

    /** [dy] is the finger's vertical movement: negative = content moves up (scrolling down the list). */
    fun onDrag(dy: Float) {
        if (dy == 0f) return
        // A change of direction starts the count again.
        if ((dy < 0f) != (travelled < 0f)) travelled = 0f
        travelled += dy
        if (dy > 0f) {
            hidden = false
        } else if (travelled <= -hideAfterPx) {
            hidden = true
        }
    }

    /** A drag the content could not use (already at the top, or too short to scroll) brings the bar back. */
    fun onUnusedDrag(dy: Float) {
        if (dy > 0f) show()
    }

    fun show() {
        hidden = false
        travelled = 0f
    }

    /** Hooks the tracker onto whatever scrolls inside the connection's subtree. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) onDrag(available.y)
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) onUnusedDrag(available.y)
            return Offset.Zero
        }
    }
}
