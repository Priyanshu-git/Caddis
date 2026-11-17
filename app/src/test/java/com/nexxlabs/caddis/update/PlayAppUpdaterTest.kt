package com.nexxlabs.caddis.update

import android.app.Application
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** [PlayAppUpdater] against Google Play's own fake, so the mapping from Play's answers to [UpdateState] is checked. */
@RunWith(RobolectricTestRunner::class)
class PlayAppUpdaterTest {

    private lateinit var play: FakeAppUpdateManager
    private lateinit var updater: PlayAppUpdater
    private var launched = 0

    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) { launched++ }
        override fun unregister() = Unit
        override val contract = ActivityResultContracts.StartIntentSenderForResult()
    }

    @Before
    fun setUp() {
        play = FakeAppUpdateManager(ApplicationProvider.getApplicationContext<Application>())
        updater = PlayAppUpdater(play)
    }

    /** Play answers on the main thread, which the test is on: run the work elsewhere and keep the looper moving. */
    private fun <T> waitFor(block: suspend () -> T): T {
        val work = CoroutineScope(Dispatchers.Default).async { block() }
        while (!work.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return runBlocking { work.await() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun nothingWaiting_meansNothingToOffer() {
        waitFor { updater.refresh() }

        assertEquals(UpdateState.None, updater.state.value)
    }

    @Test
    fun aNewerVersion_isOffered_withItsVersionCode() {
        play.setUpdateAvailable(9)

        waitFor { updater.refresh() }

        assertEquals(UpdateState.Available(9), updater.state.value)
    }

    @Test
    fun startingOpensPlaysDialog_thenDownloadsInTheBackground_thenIsReady() {
        play.setUpdateAvailable(9)
        waitFor { updater.refresh() }

        updater.start(launcher)
        idle()
        assertTrue(play.isConfirmationDialogVisible)
        assertEquals(AppUpdateType.FLEXIBLE, play.typeForUpdateInProgress)

        play.userAcceptsUpdate()
        play.downloadStarts()
        idle()
        assertEquals(UpdateState.Downloading, updater.state.value)

        play.downloadCompletes()
        idle()
        assertEquals(UpdateState.Ready, updater.state.value)

        updater.restart()
        idle()
        assertTrue(play.isInstallSplashScreenVisible)
    }

    @Test
    fun aDeclinedDialog_leavesTheUpdateAvailable() {
        play.setUpdateAvailable(9)
        waitFor { updater.refresh() }
        updater.start(launcher)
        idle()

        play.userRejectsUpdate()
        idle()
        waitFor { updater.refresh() }

        assertEquals(UpdateState.Available(9), updater.state.value)
    }

    @Test
    fun aFailedDownload_canBeStartedAgain() {
        play.setUpdateAvailable(9)
        waitFor { updater.refresh() }
        updater.start(launcher)
        play.userAcceptsUpdate()
        play.downloadStarts()
        idle()

        play.downloadFails()
        idle()

        assertEquals(UpdateState.Available(9), updater.state.value)
    }

    @Test
    fun anUpdateDownloadedEarlier_isReadyAtOnce() {
        play.setUpdateAvailable(9)
        waitFor { updater.refresh() }
        updater.start(launcher)
        play.userAcceptsUpdate()
        play.downloadStarts()
        play.downloadCompletes()
        idle()

        // A fresh run of the app asks Play again and finds the finished download.
        val later = PlayAppUpdater(play)
        waitFor { later.refresh() }

        assertEquals(UpdateState.Ready, later.state.value)
    }

    @Test
    fun restartDoesNothingUntilTheDownloadIsDone() {
        play.setUpdateAvailable(9)
        waitFor { updater.refresh() }

        updater.restart()
        idle()

        assertFalse(play.isInstallSplashScreenVisible)
    }

    @Test
    fun startDoesNothingWithoutAnOffer() {
        waitFor { updater.refresh() }

        updater.start(launcher)
        idle()

        assertFalse(play.isConfirmationDialogVisible)
    }
}
