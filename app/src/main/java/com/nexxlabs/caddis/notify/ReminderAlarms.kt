package com.nexxlabs.caddis.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.data.SettingsRepository
import com.nexxlabs.caddis.domain.schedule.ItemReminder
import com.nexxlabs.caddis.domain.schedule.ReminderSweep
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Remind me": one alarm for the earliest reminder, and the notification when it goes off. The phone's own
 * alarm clock keeps it exact; there is no server. Everything runs through [run], which first sends what has
 * come due (including anything missed while the phone was off) and then sets the alarm for the next one.
 */
@Singleton
class ReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LibraryRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    /** False when Android 12+ has "Alarms and reminders" switched off for Caddis; reminders then arrive a little late. */
    fun canScheduleExact(): Boolean = canScheduleExact(context)

    /** Sends the reminders that are due, then sets the alarm for the next one. Safe to call as often as needed. */
    suspend fun run() = lock.withLock {
        val now = clock.instant()
        val items = repo.reminders()
        // A first run, or a clock set back, never counts the future as handled.
        val after = minOf(settings.current().lastReminderSweep ?: now, now)
        ReminderSweep.due(items, after, now).forEach { notifier.showReminder(it, now) }
        settings.markReminderSweep(now)
        schedule(ReminderSweep.next(items, now))
    }

    /** Takes a reminder's notification away once the person has dealt with the save from inside the app. */
    fun dismissNotification(itemId: String) = notifier.cancelReminder(itemId)

    /** "In 1 hour" and "Tomorrow" on the notification. */
    suspend fun moveTo(itemId: String, at: Instant) {
        repo.setReminder(itemId, at)
        notifier.cancelReminder(itemId)
        run()
    }

    suspend fun snoozeAnHour(itemId: String) = moveTo(itemId, ItemReminder.inOneHour(clock.instant().atZone(clock.zone)))

    suspend fun tomorrow(itemId: String) = moveTo(itemId, ItemReminder.tomorrowMorning(clock.instant().atZone(clock.zone)))

    private fun schedule(at: Instant?) {
        val pi = pendingIntent()
        if (at == null) {
            alarms.cancel(pi)
            return
        }
        val ms = at.toEpochMilli()
        try {
            if (canScheduleExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else alarms.setWindow(AlarmManager.RTC_WAKEUP, ms, CLOSE_WINDOW_MS, pi)
        } catch (e: SecurityException) {
            // The switch was turned off between the check and the call.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, ms, CLOSE_WINDOW_MS, pi)
        }
    }

    companion object {
        /**
         * Without the exact-alarm switch the alarm gets a window instead. Android's own choice for a plain inexact
         * alarm can be an hour or more away, so ask for the shortest window it allows (10 minutes).
         */
        private const val CLOSE_WINDOW_MS = 10 * 60 * 1000L

        /** The same check without the injected class, for the sheet that explains what is switched off. */
        fun canScheduleExact(context: Context): Boolean =
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Hears the alarm, the phone starting, the app being updated, the clock or time zone changing, the exact-alarm
 * switch changing, and the buttons on a reminder. Each one just runs [ReminderAlarms.run] (after any button's move).
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var alarms: ReminderAlarms
    @Inject lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        appScope.launch {
            try {
                when {
                    intent.action == ACTION_IN_AN_HOUR && itemId != null -> alarms.snoozeAnHour(itemId)
                    intent.action == ACTION_TOMORROW && itemId != null -> alarms.tomorrow(itemId)
                    else -> alarms.run()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.nexxlabs.caddis.REMINDER_ALARM"
        const val ACTION_IN_AN_HOUR = "com.nexxlabs.caddis.REMINDER_IN_AN_HOUR"
        const val ACTION_TOMORROW = "com.nexxlabs.caddis.REMINDER_TOMORROW"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
