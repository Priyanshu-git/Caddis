package com.nexxlabs.caddis.update

import com.nexxlabs.caddis.data.SettingsRepository
import com.nexxlabs.caddis.domain.update.UpdatePrompts
import com.nexxlabs.caddis.ui.Messenger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when the main screen opens Google Play's update dialog on its own ([UpdatePrompts]) and says
 * once per run of the app when a downloaded update is ready. The share sheet never prompts.
 */
@Singleton
class UpdatePrompter @Inject constructor(
    private val updater: AppUpdater,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val messenger: Messenger,
) {
    private var announcedReady = false

    /**
     * Runs while the app is on screen: checks Google Play once, opens the dialog through [open] if it is due,
     * then keeps watching for the download to finish. Cancel it to stop.
     */
    suspend fun run(open: () -> Unit) = coroutineScope {
        launch {
            updater.state.collect { state ->
                if (state == UpdateState.Ready && !announcedReady) {
                    announcedReady = true
                    messenger.show("Update ready.", actionLabel = "Restart") { updater.restart() }
                }
            }
        }
        check(open)
    }

    private suspend fun check(open: () -> Unit) {
        val current = settings.current()
        if (!current.onboarded) return
        updater.refresh()
        val state = updater.state.value as? UpdateState.Available ?: return
        val now = clock.instant()
        if (UpdatePrompts.shouldAsk(state.versionCode, current.updateAsked, now, current.onboarded)) {
            // Recorded before the dialog opens, so leaving the app for it can never ask twice.
            settings.markUpdateAsked(state.versionCode, now)
            open()
        }
    }
}
