package com.nexxlabs.caddis.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.nexxlabs.caddis.MutableClock
import com.nexxlabs.caddis.data.SettingsRepository
import com.nexxlabs.caddis.ui.Messenger
import com.nexxlabs.caddis.ui.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

private class FakeUpdater(initial: UpdateState = UpdateState.None) : AppUpdater {
    val current = MutableStateFlow(initial)
    override val state: StateFlow<UpdateState> = current
    var refreshes = 0
    var restarts = 0

    override suspend fun refresh() { refreshes++ }
    override fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit
    override fun restart() { restarts++ }
}

@RunWith(RobolectricTestRunner::class)
class UpdatePrompterTest {

    private val clock = MutableClock(Instant.parse("2026-11-17T10:00:00Z"))
    private val messenger = Messenger()
    private lateinit var settings: SettingsRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        val dir = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
        file = File(dir, "update-${UUID.randomUUID()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create { file })
    }

    @After
    fun tearDown() { file.delete() }

    /** DataStore works on its own thread, so give a run real time to finish what it is going to do. */
    private suspend fun settle() = delay(400)

    private fun prompter(updater: FakeUpdater) = UpdatePrompter(updater, settings, clock, messenger)

    @Test
    fun anAvailableUpdateOpensTheDialog_andIsRemembered() = runBlocking {
        settings.setOnboarded()
        var opened = 0

        val job = launch(Dispatchers.Default) { prompter(FakeUpdater(UpdateState.Available(7))).run { opened++ } }
        settle()
        job.cancelAndJoin()

        assertEquals(1, opened)
        assertEquals(7, settings.current().updateAsked?.versionCode)
        assertEquals(clock.now, settings.current().updateAsked?.at)
    }

    @Test
    fun theSameVersionIsNotAskedAgainSoon_butIsAfterAWeek() = runBlocking {
        settings.setOnboarded()
        val updater = FakeUpdater(UpdateState.Available(7))
        var opened = 0

        suspend fun launchOnce() {
            val job = launch(Dispatchers.Default) { prompter(updater).run { opened++ } }
            settle()
            job.cancelAndJoin()
        }
        launchOnce()
        clock.advance(Duration.ofDays(2))
        launchOnce()
        assertEquals(1, opened)

        clock.advance(Duration.ofDays(5))
        launchOnce()
        assertEquals(2, opened)
    }

    @Test
    fun nothingHappensBeforeOnboardingIsDone() = runBlocking {
        val updater = FakeUpdater(UpdateState.Available(7))
        var opened = 0

        val job = launch(Dispatchers.Default) { prompter(updater).run { opened++ } }
        settle()
        job.cancelAndJoin()

        assertEquals(0, opened)
        assertEquals(0, updater.refreshes)
        assertNull(settings.current().updateAsked)
    }

    @Test
    fun noUpdateMeansNoDialog() = runBlocking {
        settings.setOnboarded()
        var opened = 0

        val job = launch(Dispatchers.Default) { prompter(FakeUpdater(UpdateState.None)).run { opened++ } }
        settle()
        job.cancelAndJoin()

        assertEquals(0, opened)
        assertNull(settings.current().updateAsked)
    }

    @Test
    fun aFinishedDownloadIsAnnouncedOnce_withARestartAction() = runBlocking {
        settings.setOnboarded()
        val updater = FakeUpdater(UpdateState.Downloading)
        val toasts = mutableListOf<Toast>()
        val collector = launch(Dispatchers.Default) { messenger.toasts.collect { toasts += it } }
        val p = prompter(updater)

        val job = launch(Dispatchers.Default) { p.run { } }
        settle()
        assertEquals(0, toasts.size)

        updater.current.value = UpdateState.Ready
        settle()
        assertEquals(listOf("Update ready."), toasts.map { it.message })
        assertEquals("Restart", toasts.single().actionLabel)
        val action = toasts.single().undo!!
        action()
        assertEquals(1, updater.restarts)

        // Coming back to the app with the update still waiting does not say it again.
        job.cancelAndJoin()
        val again = launch(Dispatchers.Default) { p.run { } }
        settle()
        again.cancelAndJoin()
        collector.cancelAndJoin()
        assertEquals(1, toasts.size)
    }
}
