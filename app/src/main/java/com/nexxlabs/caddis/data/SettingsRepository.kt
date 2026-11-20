package com.nexxlabs.caddis.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexxlabs.caddis.domain.schedule.ReminderState
import com.nexxlabs.caddis.domain.update.UpdatePrompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val onboarded: Boolean = false,
    /** BRB-04: notifications are opt-in. */
    val dailyReminder: Boolean = false,
    val weeklySummary: Boolean = false,
    /** NFR battery: pause background work on low battery if chosen. */
    val pauseOnLowBattery: Boolean = true,
    val lastDailySent: LocalDate? = null,
    val lastWeeklySentWeek: LocalDate? = null,
    /** The last time Google Play's update dialog was opened on its own (see [UpdatePrompts]). */
    val updateAsked: UpdatePrompts.Asked? = null,
) {
    fun reminderState() = ReminderState(dailyReminder, weeklySummary, lastDailySent, lastWeeklySentWeek)
}

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val daily = booleanPreferencesKey("daily_reminder")
        val weekly = booleanPreferencesKey("weekly_summary")
        val battery = booleanPreferencesKey("pause_low_battery")
        val lastDaily = stringPreferencesKey("last_daily_sent")
        val lastWeekly = stringPreferencesKey("last_weekly_week")
        val updateVersion = intPreferencesKey("update_asked_version")
        val updateAt = longPreferencesKey("update_asked_at")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            onboarded = p[K.onboarded] ?: false,
            dailyReminder = p[K.daily] ?: false,
            weeklySummary = p[K.weekly] ?: false,
            pauseOnLowBattery = p[K.battery] ?: true,
            lastDailySent = p[K.lastDaily]?.let(LocalDate::parse),
            lastWeeklySentWeek = p[K.lastWeekly]?.let(LocalDate::parse),
            updateAsked = p[K.updateVersion]?.let { v -> p[K.updateAt]?.let { UpdatePrompts.Asked(v, Instant.ofEpochMilli(it)) } },
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboarded() = store.edit { it[K.onboarded] = true }
    suspend fun setDailyReminder(on: Boolean) = store.edit { it[K.daily] = on }
    suspend fun setWeeklySummary(on: Boolean) = store.edit { it[K.weekly] = on }
    suspend fun setPauseOnLowBattery(on: Boolean) = store.edit { it[K.battery] = on }
    suspend fun markDailySent(date: LocalDate) = store.edit { it[K.lastDaily] = date.toString() }
    suspend fun markWeeklySent(date: LocalDate, week: LocalDate) = store.edit {
        it[K.lastDaily] = date.toString()
        it[K.lastWeekly] = week.toString()
    }

    suspend fun markUpdateAsked(versionCode: Int, at: Instant) = store.edit {
        it[K.updateVersion] = versionCode
        it[K.updateAt] = at.toEpochMilli()
    }

    /** Keeps onboarding done, resets everything else. */
    suspend fun reset() = store.edit { p ->
        val onboarded = p[K.onboarded] ?: false
        p.clear()
        p[K.onboarded] = onboarded
    }
}
