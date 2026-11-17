package com.nexxlabs.caddis.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where a newer Caddis on Google Play stands. */
sealed interface UpdateState {
    /** Nothing to offer: up to date, not installed from Google Play, or Play could not be reached. */
    data object None : UpdateState

    /** A newer version is waiting; [versionCode] is the one Play offers. */
    data class Available(val versionCode: Int) : UpdateState

    /** Google Play is downloading it in the background. */
    data object Downloading : UpdateState

    /** Downloaded; one restart installs it. */
    data object Ready : UpdateState
}

/** Google Play's flexible in-app update: the download happens while Caddis stays usable. */
interface AppUpdater {
    val state: StateFlow<UpdateState>

    /** Asks Google Play what is waiting and updates [state]. Never throws; a failed check leaves [state] as it was. */
    suspend fun refresh()

    /** Opens Play's update dialog for the version in [UpdateState.Available]. Does nothing otherwise. */
    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Installs a downloaded update; the app restarts. Does nothing unless [UpdateState.Ready]. */
    fun restart()
}

@Singleton
class PlayAppUpdater @Inject constructor(private val manager: AppUpdateManager) : AppUpdater {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    override val state: StateFlow<UpdateState> = _state

    private var info: AppUpdateInfo? = null

    private val listener = InstallStateUpdatedListener { install ->
        when (install.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> _state.value = UpdateState.Downloading
            InstallStatus.DOWNLOADED -> _state.value = UpdateState.Ready
            InstallStatus.INSTALLED -> _state.value = UpdateState.None
            // Failed or cancelled: the update is still waiting, so it can be started again.
            InstallStatus.FAILED, InstallStatus.CANCELED -> _state.value = info?.let(::available) ?: UpdateState.None
            else -> Unit
        }
    }

    init {
        manager.registerListener(listener)
    }

    override suspend fun refresh() {
        val latest = try {
            manager.requestAppUpdateInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return // Not installed from Play, no Play services, or offline: nothing to offer.
        }
        info = latest
        _state.value = when {
            latest.installStatus() == InstallStatus.DOWNLOADED -> UpdateState.Ready
            latest.installStatus() == InstallStatus.DOWNLOADING || latest.installStatus() == InstallStatus.PENDING -> UpdateState.Downloading
            else -> available(latest)
        }
    }

    override fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val latest = info ?: return
        if (_state.value !is UpdateState.Available) return
        runCatching { manager.startUpdateFlowForResult(latest, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()) }
    }

    override fun restart() {
        if (_state.value == UpdateState.Ready) runCatching { manager.completeUpdate() }
    }

    private fun available(i: AppUpdateInfo): UpdateState =
        if (i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && i.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            UpdateState.Available(i.availableVersionCode())
        } else {
            UpdateState.None
        }
}
