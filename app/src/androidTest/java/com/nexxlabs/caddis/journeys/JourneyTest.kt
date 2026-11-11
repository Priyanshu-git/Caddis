package com.nexxlabs.caddis.journeys

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.nexxlabs.caddis.FakeDetails
import com.nexxlabs.caddis.MainActivity
import com.nexxlabs.caddis.RecordingAnalytics
import com.nexxlabs.caddis.TestClock
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.data.SaveOutcome
import com.nexxlabs.caddis.data.SettingsRepository
import com.nexxlabs.caddis.data.db.CaddisDatabase
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.link.LinkCheck
import com.nexxlabs.caddis.domain.link.Links
import com.nexxlabs.caddis.domain.model.Item
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * Base for the journey tests: the real app (activities, ViewModels, Room, DataStore) running on in-memory
 * storage, a clock the test moves, and no network. A journey drives the UI the way a person would and then
 * checks both what is on screen and what was kept in the library.
 */
@OptIn(ExperimentalTestApi::class)
abstract class JourneyTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var repo: LibraryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var details: FakeDetails
    @Inject lateinit var analytics: RecordingAnalytics
    @Inject lateinit var db: CaddisDatabase

    protected val testClock: TestClock get() = clock as TestClock
    protected val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    @Before
    fun setUpJourney() {
        hilt.inject()
        runBlocking { repo.seedDefaultsIfNeeded() }
        // Turning a reminder on asks for this on Android 13+; granting it up front keeps the system dialog out of the way.
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDownJourney() {
        scenarios.forEach { runCatching { it.close() } }
    }

    // ---------- launching ----------

    /** Opens the app. Onboarding is already done unless [onboarded] is false. */
    protected fun launchApp(route: String? = null, onboarded: Boolean = true): ActivityScenario<MainActivity> {
        if (onboarded) runBlocking { settings.setOnboarded() }
        val intent = Intent(context, MainActivity::class.java).apply { route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) } }
        return ActivityScenario.launch<MainActivity>(intent).also { scenarios += it }
    }

    protected fun <A : android.app.Activity> track(scenario: ActivityScenario<A>): ActivityScenario<A> = scenario.also { scenarios += it }

    // ---------- library data ----------

    /** Saves a link as the person would (before any UI), optionally with a title and a chosen intent. */
    protected fun save(url: String, title: String = "", intent: String? = null, reason: String = ""): Item = runBlocking {
        val link = Links.check(url) as LinkCheck.Valid
        val saved = (repo.saveLink(link, AnalyticsEvent.SaveEntry.IN_APP, titleHint = title) as SaveOutcome.Saved).item
        if (intent != null) repo.setIntent(saved.id, intent)
        if (reason.isNotEmpty()) repo.setReason(saved.id, reason)
        saved
    }

    protected fun saveNote(title: String, body: String = ""): Item = runBlocking { repo.saveNote(title, body) }

    protected fun library(): List<Item> = runBlocking { repo.observeLibrary().first() }

    protected fun trash(): List<Item> = runBlocking { repo.observeTrash().first() }

    protected fun item(id: String): Item = checkNotNull(runBlocking { repo.get(id) }) { "No item $id" }

    protected fun byTitle(title: String): Item = library().first { it.displayTitle == title }

    /** Time passes: saves become old enough to come back on Home, or to be tidied. */
    protected fun daysLater(days: Long) = testClock.advance(Duration.ofDays(days))

    /**
     * Waits for something that happens off the main thread (a save on the app scope, a debounced edit).
     * The screen is kept moving meanwhile: the compose test clock only advances when the test lets it.
     */
    protected fun eventually(what: String, timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for: $what")
            try {
                compose.waitForIdle()
            } catch (e: IllegalStateException) {
                // No screen left (every activity closed): nothing to move.
            }
            Thread.sleep(50)
        }
    }

    // ---------- finding things ----------

    protected fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)

    /** A [CButton] or chip: tappable text. */
    protected fun button(text: String) = hasText(text) and role(Role.Button)

    protected fun tab(text: String) = hasText(text) and role(Role.Tab)

    protected fun field(label: String) = hasContentDescription(label) and hasSetTextAction()

    protected fun described(text: String) = hasContentDescription(text)

    protected fun exists(m: SemanticsMatcher): Boolean = count(m) > 0

    /** No screen at all (every activity closed) counts as nothing showing. */
    protected fun count(m: SemanticsMatcher): Int = try {
        compose.onAllNodes(m).fetchSemanticsNodes().size
    } catch (e: IllegalStateException) {
        if (e.message?.contains("No compose hierarchies") == true) 0 else throw e
    }

    protected fun await(m: SemanticsMatcher, timeoutMs: Long = 10_000) {
        try {
            compose.waitUntil(timeoutMs) { exists(m) }
        } catch (e: Throwable) {
            throw AssertionError("Timed out waiting for: ${m.description}\nOn screen:\n${screen()}", e)
        }
    }

    protected fun awaitGone(m: SemanticsMatcher, timeoutMs: Long = 10_000) {
        try {
            compose.waitUntil(timeoutMs) { !exists(m) }
        } catch (e: Throwable) {
            throw AssertionError("Still showing after ${timeoutMs}ms: ${m.description}\nOn screen:\n${screen()}", e)
        }
    }

    /** What is on screen right now, for failure messages. */
    protected fun screen(): String = try {
        compose.onAllNodes(isRoot()).printToString(maxDepth = 14).take(8_000)
    } catch (e: Throwable) {
        "(nothing: ${e.message?.take(80)})"
    }

    protected fun awaitText(text: String, substring: Boolean = false) = await(hasText(text, substring = substring))

    protected fun awaitNoText(text: String, substring: Boolean = false) = awaitGone(hasText(text, substring = substring))

    protected fun assertShowing(vararg texts: String) = texts.forEach { awaitText(it) }

    // ---------- doing things ----------

    protected fun tap(m: SemanticsMatcher) {
        await(m)
        val node = compose.onAllNodes(m)[0]
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    protected fun tapText(text: String) = tap(hasText(text))

    protected fun tapButton(text: String) = tap(button(text))

    protected fun tapTab(text: String) = tap(tab(text))

    protected fun tapDescribed(description: String) = tap(described(description))

    protected fun type(label: String, text: String) {
        await(field(label))
        compose.onAllNodes(field(label))[0].performTextInput(text)
    }

    protected fun replaceText(label: String, text: String) {
        await(field(label))
        compose.onAllNodes(field(label))[0].performTextReplacement(text)
    }

    protected fun back() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    protected fun undo() = tapText("Undo")

    /** Home, Library or Profile in the bottom bar. */
    protected fun goTo(tabName: String) = tapTab(tabName)
}
