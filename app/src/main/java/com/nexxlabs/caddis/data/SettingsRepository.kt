package com.nexxlabs.caddis.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexxlabs.caddis.domain.schedule.QuietHours
import com.nexxlabs.caddis.domain.schedule.ReminderState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val onboarded: Boolean = false,
    /** BRB-04: notifications are opt-in. */
    val dailyReminder: Boolean = false,
    val weeklySummary: Boolean = false,
    val quietHours: QuietHours = QuietHours.TEN_TO_SEVEN,
    /** NFR battery and data: pause background work on low battery / mobile data if chosen. */
    val pauseOnLowBattery: Boolean = true,
    val pauseOnMobileData: Boolean = false,
    val lastDailySent: LocalDate? = null,
    val lastWeeklySentWeek: LocalDate? = null,
) {
    fun reminderState() = ReminderState(dailyReminder, weeklySummary, quietHours, lastDailySent, lastWeeklySentWeek)
}

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val daily = booleanPreferencesKey("daily_reminder")
        val weekly = booleanPreferencesKey("weekly_summary")
        val quiet = intPreferencesKey("quiet_hours")
        val battery = booleanPreferencesKey("pause_low_battery")
        val metered = booleanPreferencesKey("pause_mobile_data")
        val lastDaily = stringPreferencesKey("last_daily_sent")
        val lastWeekly = stringPreferencesKey("last_weekly_week")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            onboarded = p[K.onboarded] ?: false,
            dailyReminder = p[K.daily] ?: false,
            weeklySummary = p[K.weekly] ?: false,
            quietHours = QuietHours.entries.getOrElse(p[K.quiet] ?: 0) { QuietHours.TEN_TO_SEVEN },
            pauseOnLowBattery = p[K.battery] ?: true,
            pauseOnMobileData = p[K.metered] ?: false,
            lastDailySent = p[K.lastDaily]?.let(LocalDate::parse),
            lastWeeklySentWeek = p[K.lastWeekly]?.let(LocalDate::parse),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboarded() = store.edit { it[K.onboarded] = true }
    suspend fun setDailyReminder(on: Boolean) = store.edit { it[K.daily] = on }
    suspend fun setWeeklySummary(on: Boolean) = store.edit { it[K.weekly] = on }
    suspend fun setQuietHours(q: QuietHours) = store.edit { it[K.quiet] = q.ordinal }
    suspend fun setPauseOnLowBattery(on: Boolean) = store.edit { it[K.battery] = on }
    suspend fun setPauseOnMobileData(on: Boolean) = store.edit { it[K.metered] = on }
    suspend fun markDailySent(date: LocalDate) = store.edit { it[K.lastDaily] = date.toString() }
    suspend fun markWeeklySent(date: LocalDate, week: LocalDate) = store.edit {
        it[K.lastDaily] = date.toString()
        it[K.lastWeekly] = week.toString()
    }

    /** Keeps onboarding done, resets everything else. */
    suspend fun reset() = store.edit { p ->
        val onboarded = p[K.onboarded] ?: false
        p.clear()
        p[K.onboarded] = onboarded
    }
}
