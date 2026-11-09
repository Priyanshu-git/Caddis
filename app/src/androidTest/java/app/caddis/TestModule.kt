package app.caddis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import app.caddis.data.DetailsScheduler
import app.caddis.data.LibraryRepository
import app.caddis.data.db.CaddisDatabase
import app.caddis.di.AppModule
import app.caddis.di.BindingsModule
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.analytics.AnalyticsSink
import app.caddis.domain.details.LinkDetails
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A clock the tests can move: Wed 4 Nov 2026, 11:08 in India, so Home's time-of-day rules are the same on every run. */
class TestClock(@Volatile var now: Instant = START, private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }

    companion object {
        val START: Instant = Instant.parse("2026-11-04T05:38:00Z")
    }
}

class RecordingAnalytics : AnalyticsSink {
    val events = CopyOnWriteArrayList<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent) { events += event }
}

/**
 * Stands in for WorkManager and the network. By default a fetch "gives up" (nothing could be read), so a
 * save shows the title Caddis builds from the link. Register a page with [serve] to have details arrive,
 * or [hold] to keep them from arriving until [release].
 */
@Singleton
class FakeDetails @Inject constructor(
    private val repo: dagger.Lazy<LibraryRepository>,
    private val scope: CoroutineScope,
) : DetailsScheduler {
    val scheduled = CopyOnWriteArrayList<String>()
    private val pages = ConcurrentHashMap<String, LinkDetails>()
    private val held = CopyOnWriteArrayList<String>()
    @Volatile private var holding = false

    fun serve(url: String, details: LinkDetails) { pages[url] = details }

    fun hold() { holding = true }

    fun release() {
        holding = false
        held.toList().also { held.clear() }.forEach(::answer)
    }

    override fun schedule(itemId: String) {
        scheduled += itemId
        if (holding) held += itemId else answer(itemId)
    }

    private fun answer(id: String) {
        scope.launch {
            val r = repo.get()
            val url = r.get(id)?.url ?: return@launch
            val page = pages[url]
            if (page != null) r.applyDetails(id, page, forUrl = url) else r.markDetailsAttempted(id, forUrl = url)
        }
    }
}

/** Swaps the app's real storage, clock, network and background work for in-memory fakes. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class, BindingsModule::class])
object TestModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CaddisDatabase =
        Room.inMemoryDatabaseBuilder(context, CaddisDatabase::class.java).build()

    /** A new file per test: DataStore refuses two live instances on one file. */
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(context.cacheDir, "test-settings-${UUID.randomUUID()}.preferences_pb") }

    @Provides
    @Singleton
    fun clock(): Clock = TestClock()

    @Provides
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Nothing in a journey test should reach the network. */
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun recordingAnalytics(): RecordingAnalytics = RecordingAnalytics()

    @Provides
    @Singleton
    fun analytics(sink: RecordingAnalytics): AnalyticsSink = sink

    @Provides
    @Singleton
    fun detailsScheduler(fake: FakeDetails): DetailsScheduler = fake
}
