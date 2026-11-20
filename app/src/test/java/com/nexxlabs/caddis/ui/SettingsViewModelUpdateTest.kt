package com.nexxlabs.caddis.ui

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nexxlabs.caddis.Fixture
import com.nexxlabs.caddis.data.SettingsRepository
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.ui.settings.SettingsViewModel
import com.nexxlabs.caddis.update.AppUpdater
import com.nexxlabs.caddis.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

/** "Check for updates" in Settings: asks Play now, then opens its dialog or records where things stand. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelUpdateTest {

    private class StubUpdater(initial: UpdateState = UpdateState.None, var reachable: Boolean = true) : AppUpdater {
        val current = MutableStateFlow(initial)
        override val state: StateFlow<UpdateState> = current
        var refreshes = 0
        var starts = 0
        var restarts = 0

        override suspend fun refresh(): Boolean { refreshes++; return reachable }
        override fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) { starts++ }
        override fun restart() { restarts++ }
    }

    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract = ActivityResultContracts.StartIntentSenderForResult()
    }

    private lateinit var f: Fixture
    private lateinit var file: File
    private lateinit var vm: SettingsViewModel
    private val messenger = Messenger()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = Fixture()
        file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
        file.delete()
        f.close()
    }

    private fun viewModel(updater: AppUpdater) = SettingsViewModel(
        ApplicationProvider.getApplicationContext(), f.repo, SettingsRepository(PreferenceDataStoreFactory.create { file }),
        messenger, f.analytics, f.clock, updater,
    ).also { vm = it }

    private fun check(updater: StubUpdater): SettingsViewModel.CheckStatus {
        viewModel(updater).checkForUpdate(launcher)
        return vm.checkStatus.value
    }

    @Test
    fun upToDate_isShownOnlyAfterPlayAnswered() = runTest {
        val updater = StubUpdater(UpdateState.None)

        assertEquals(SettingsViewModel.CheckStatus.UpToDate, check(updater))

        assertEquals(1, updater.refreshes)
        assertEquals(0, updater.starts)
    }

    @Test
    fun whenPlayCannotBeReached_itIsNeverUpToDate() = runTest {
        val updater = StubUpdater(UpdateState.None, reachable = false)

        assertEquals(SettingsViewModel.CheckStatus.Unreachable, check(updater))
    }

    @Test
    fun anUpdateWaiting_opensPlaysDialog() = runTest {
        val updater = StubUpdater(UpdateState.Available(9))

        assertEquals(SettingsViewModel.CheckStatus.Idle, check(updater))

        assertEquals(1, updater.starts)
        assertEquals(0, updater.restarts)
    }

    @Test
    fun aDownloadedUpdate_restartsTheApp() = runTest {
        val updater = StubUpdater(UpdateState.Ready)

        assertEquals(SettingsViewModel.CheckStatus.Idle, check(updater))

        assertEquals(1, updater.restarts)
        assertEquals(0, updater.starts)
    }

    @Test
    fun anUpdateAlreadyDownloading_doesNothingMore() = runTest {
        val updater = StubUpdater(UpdateState.Downloading)

        assertEquals(SettingsViewModel.CheckStatus.Idle, check(updater))

        assertEquals(0, updater.starts)
        assertEquals(0, updater.restarts)
    }

    @Test
    fun trashCount_followsWhatIsDeleted_andRestored() = runTest {
        f.repo.seedDefaultsIfNeeded()
        val note = f.repo.saveNote("Gift ideas")
        viewModel(StubUpdater())
        assertEquals(0, vm.trashCount.value)

        f.repo.delete(note.id, AnalyticsEvent.DeleteSource.ITEM_PAGE)
        waitUntil { vm.trashCount.value == 1 }

        f.repo.restore(note.id)
        waitUntil { vm.trashCount.value == 0 }
    }

    /** Room answers on its own thread, so give the flow real time to catch up. */
    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(true, condition())
    }
}
