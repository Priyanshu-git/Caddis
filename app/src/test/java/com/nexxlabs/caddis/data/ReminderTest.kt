package com.nexxlabs.caddis.data

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.nexxlabs.caddis.Fixture
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent.SaveEntry
import com.nexxlabs.caddis.domain.link.LinkCheck
import com.nexxlabs.caddis.domain.link.Links
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ReturnRule
import com.nexxlabs.caddis.domain.schedule.HomePicker
import com.nexxlabs.caddis.notify.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.TimeZone

/** "Remind me": the stored reminder, what clears it, the alarm, and the notification that goes out. */
@RunWith(RobolectricTestRunner::class)
class ReminderTest {
    private lateinit var f: Fixture
    private lateinit var app: Application
    private lateinit var systemZone: TimeZone
    private val repo get() = f.repo
    private val clock get() = f.clock

    @Before fun setUp() = runBlocking {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        f = Fixture()
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        f.notifier.createChannels()
        repo.seedDefaultsIfNeeded()
    }

    @After fun tearDown() {
        f.close()
        TimeZone.setDefault(systemZone)
    }

    private suspend fun save(path: String): Item =
        (repo.saveLink(Links.check("https://example.com/$path") as LinkCheck.Valid, SaveEntry.IN_APP, titleHint = "Title $path") as SaveOutcome.Saved).item

    /** Wed 4 Nov 2026 at [h]:[m] Kolkata time (the fixture's "today"). */
    private fun at(h: Int, m: Int = 0): Instant = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, clock.zone).toInstant()

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun alarmManager() = app.getSystemService(AlarmManager::class.java)

    private fun nextAlarm() = shadowOf(alarmManager()).peekNextScheduledAlarm()

    // ---------- the stored reminder ----------

    @Test
    fun `a reminder is stored as a return at that moment, and clearing it puts the item back to its intent rule`() = runBlocking {
        val item = save("a")
        repo.setReminder(item.id, at(23))
        val set = repo.observeItem(item.id).first()!!
        assertEquals(ReturnRule.REMINDER, set.returnRule)
        assertEquals(at(23), set.returnAt)
        assertTrue(set.hasReminder)

        repo.clearReminder(item.id)
        val cleared = repo.observeItem(item.id).first()!!
        assertEquals(ReturnRule.DEFAULT, cleared.returnRule)
        assertNull(cleared.returnAt)
    }

    @Test
    fun `clearing a reminder leaves a Later date alone`() = runBlocking {
        val item = save("b")
        repo.bringBackLater(item.id, ReturnRule.NEXT_WEEK)
        val before = repo.observeItem(item.id).first()!!
        repo.clearReminder(item.id)
        val after = repo.observeItem(item.id).first()!!
        assertEquals(ReturnRule.NEXT_WEEK, after.returnRule)
        assertEquals(before.returnAt, after.returnAt)
        assertEquals(before.revision, after.revision)
    }

    @Test
    fun `setting a reminder replaces a Later date, and a Later date replaces a reminder`() = runBlocking {
        val item = save("c")
        repo.bringBackLater(item.id, ReturnRule.TONIGHT)
        repo.setReminder(item.id, at(23))
        assertEquals(ReturnRule.REMINDER, repo.observeItem(item.id).first()!!.returnRule)
        repo.bringBackLater(item.id, ReturnRule.WEEKEND)
        assertEquals(ReturnRule.WEEKEND, repo.observeItem(item.id).first()!!.returnRule)
    }

    @Test
    fun `a due reminder stays on Home across days until it is opened`() = runBlocking {
        val item = save("d")
        repo.setReminder(item.id, at(23))
        clock.now = at(23, 5)
        repo.markSurfaced(listOf(item.id))
        clock.advance(Duration.ofDays(2)) // a Later date would have fallen back by now
        repo.markSurfaced(listOf(item.id))
        val still = repo.observeItem(item.id).first()!!
        assertEquals(ReturnRule.REMINDER, still.returnRule)
        val picks = HomePicker().pick(listOf(still), repo.observeRules().first(), clock.instant().atZone(clock.zone))
        assertEquals(listOf(item.id), picks.map { it.item.id })
    }

    @Test
    fun `opening the link answers a due reminder, but not one that is still ahead`() = runBlocking {
        val item = save("e")
        repo.setReminder(item.id, at(23))
        repo.markOpened(item.id, AnalyticsEvent.OpenEntry.HOME)
        assertEquals(ReturnRule.REMINDER, repo.observeItem(item.id).first()!!.returnRule)

        clock.now = at(23, 1)
        repo.markOpened(item.id, AnalyticsEvent.OpenEntry.HOME)
        val answered = repo.observeItem(item.id).first()!!
        assertEquals(ReturnRule.DEFAULT, answered.returnRule)
        assertNull(answered.returnAt)
    }

    @Test
    fun `finishing an item ends its reminder`() = runBlocking {
        val item = save("f")
        repo.setReminder(item.id, at(23))
        repo.markDone(item.id)
        assertTrue(repo.reminders().isEmpty())
    }

    // ---------- the alarm ----------

    @Test
    fun `the alarm is set for the earliest reminder and moves when it is cleared`() = runBlocking {
        val soon = save("g1")
        val later = save("g2")
        repo.setReminder(later.id, at(23, 30))
        repo.setReminder(soon.id, at(20))
        f.alarms.run()
        assertEquals(at(20).toEpochMilli(), nextAlarm()!!.triggerAtMs)

        repo.clearReminder(soon.id)
        f.alarms.run()
        assertEquals(at(23, 30).toEpochMilli(), nextAlarm()!!.triggerAtMs)

        repo.clearReminder(later.id)
        f.alarms.run()
        assertNull(nextAlarm())
    }

    @Test
    fun `without the exact alarm switch a close alarm is still set`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(f.alarms.canScheduleExact())
        val item = save("h")
        repo.setReminder(item.id, at(23))
        f.alarms.run()
        assertEquals(at(23).toEpochMilli(), nextAlarm()!!.triggerAtMs)
    }

    // ---------- the notification ----------

    @Test
    fun `nothing goes out before the time, one notification at the time, and never twice`() = runBlocking {
        val item = save("i")
        repo.setReminder(item.id, at(23))
        f.alarms.run()
        assertTrue(notifications().isEmpty())

        clock.now = at(23)
        f.alarms.run()
        val n = notifications().single()
        assertEquals("Title i", n.extras.getString("android.title"))
        assertEquals("You asked to be reminded tonight.", n.extras.getString("android.text"))
        assertEquals(Notifier.CH_YOURS, n.channelId)
        assertEquals(3, n.actions.size)

        app.getSystemService(NotificationManager::class.java).cancelAll()
        clock.advance(Duration.ofMinutes(10))
        f.alarms.run()
        assertTrue("already sent, so not again", notifications().isEmpty())
    }

    @Test
    fun `a reminder missed while the phone was off goes out once when the app next runs`() = runBlocking {
        val item = save("j")
        repo.setReminder(item.id, at(23))
        f.alarms.run()
        clock.now = at(23).plus(Duration.ofHours(9)) // switched on again the next morning
        f.alarms.run()
        val n = notifications().single()
        assertEquals("You asked to be reminded at 11:00 pm.", n.extras.getString("android.text"))
    }

    @Test
    fun `a finished or cleared item sends nothing`() = runBlocking {
        val done = save("k1")
        val cleared = save("k2")
        repo.setReminder(done.id, at(23))
        repo.setReminder(cleared.id, at(23))
        f.alarms.run()
        repo.markDone(done.id)
        repo.clearReminder(cleared.id)
        clock.now = at(23, 1)
        f.alarms.run()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `reminders are outside the one a day limit and do not use it up`() = runBlocking {
        val a = save("l1")
        val b = save("l2")
        repo.setReminder(a.id, at(23))
        repo.setReminder(b.id, at(23))
        f.alarms.run()
        clock.now = at(23, 1)
        f.alarms.run()
        assertEquals(2, notifications().size)
        assertNull(f.settings.current().lastDailySent)
    }

    @Test
    fun `blocked notifications send nothing but the reminder still waits on Home`() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val item = save("m")
        repo.setReminder(item.id, at(23))
        f.alarms.run()
        clock.now = at(23, 1)
        f.alarms.run()
        assertTrue(notifications().isEmpty())
        assertEquals(ReturnRule.REMINDER, repo.observeItem(item.id).first()!!.returnRule)
    }

    @Test
    fun `In 1 hour and Tomorrow move the reminder, clear its notification and set the next alarm`() = runBlocking {
        val item = save("n")
        repo.setReminder(item.id, at(23))
        f.alarms.run()
        clock.now = at(23, 2)
        f.alarms.run()
        assertEquals(1, notifications().size)

        f.alarms.snoozeAnHour(item.id)
        val moved = repo.observeItem(item.id).first()!!
        assertEquals(at(23, 2).plus(Duration.ofHours(1)), moved.returnAt)
        assertTrue(notifications().isEmpty())
        assertEquals(moved.returnAt!!.toEpochMilli(), nextAlarm()!!.triggerAtMs)

        f.alarms.tomorrow(item.id)
        val tomorrow = repo.observeItem(item.id).first()!!
        assertEquals(ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, clock.zone).toInstant(), tomorrow.returnAt)
        assertNotNull(nextAlarm())
    }
}
