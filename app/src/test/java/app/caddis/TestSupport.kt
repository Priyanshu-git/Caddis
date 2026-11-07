package app.caddis

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.caddis.data.DetailsScheduler
import app.caddis.data.LibraryRepository
import app.caddis.data.db.CaddisDatabase
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.analytics.AnalyticsSink
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A clock tests can move forward. */
class MutableClock(var now: Instant, private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

class RecordingAnalytics : AnalyticsSink {
    val events = mutableListOf<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent) { events += event }
}

class RecordingScheduler : DetailsScheduler {
    val scheduled = mutableListOf<String>()
    override fun schedule(itemId: String) { scheduled += itemId }
}

class Fixture {
    /** Wed 4 Nov 2026, 11:08 IST. */
    val clock = MutableClock(Instant.parse("2026-11-04T05:38:00Z"))
    val analytics = RecordingAnalytics()
    val scheduler = RecordingScheduler()
    val db: CaddisDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CaddisDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val repo = LibraryRepository(db, clock, analytics, scheduler)

    fun close() = db.close()
}
