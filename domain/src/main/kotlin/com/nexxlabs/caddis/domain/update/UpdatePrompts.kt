package com.nexxlabs.caddis.domain.update

import java.time.Duration
import java.time.Instant

/**
 * When Caddis opens Google Play's update dialog by itself. Calm by design: a new version is offered once,
 * then again no sooner than a week later if it is still waiting, and never during first launch.
 * Asking again on request (the Profile row) is not governed by this.
 */
object UpdatePrompts {
    val REPEAT_AFTER: Duration = Duration.ofDays(7)

    /** The last time the dialog was opened on its own, and for which version. */
    data class Asked(val versionCode: Int, val at: Instant)

    /**
     * @param availableVersion the version code Google Play says is waiting.
     * @param lastAsked the previous automatic prompt, if any.
     */
    fun shouldAsk(availableVersion: Int, lastAsked: Asked?, now: Instant, onboarded: Boolean): Boolean {
        if (!onboarded) return false
        if (lastAsked == null || lastAsked.versionCode != availableVersion) return true
        // A clock set back since the last prompt must not silence it for longer than a week.
        if (lastAsked.at.isAfter(now)) return true
        return !now.isBefore(lastAsked.at.plus(REPEAT_AFTER))
    }
}
