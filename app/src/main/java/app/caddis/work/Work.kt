package app.caddis.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.caddis.data.DetailsScheduler
import app.caddis.data.LibraryRepository
import app.caddis.data.SettingsRepository
import app.caddis.data.details.DetailsFetcher
import app.caddis.data.details.FetchResult
import app.caddis.domain.schedule.HomePicker
import app.caddis.domain.schedule.ReminderDecision
import app.caddis.domain.schedule.Reminders
import app.caddis.domain.schedule.Weeks
import app.caddis.notify.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background work runs on Android's scheduler (NFR battery and data). Details are fetched one item
 * per unique job, so a retry never duplicates work (NFR reliability).
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
) : DetailsScheduler {

    private val wm get() = WorkManager.getInstance(context)

    override fun schedule(itemId: String) {
        appScope.launch {
            val s = settings.current()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (s.pauseOnMobileData) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(s.pauseOnLowBattery)
                .build()
            val req = OneTimeWorkRequestBuilder<DetailsWorker>()
                .setInputData(workDataOf(DetailsWorker.KEY_ID to itemId))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(DetailsWorker.TAG)
                .build()
            wm.enqueueUniqueWork("details-$itemId", ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun scheduleReminders() {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(ReminderWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

@HiltWorker
class DetailsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LibraryRepository,
    private val fetcher: DetailsFetcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val item = repo.get(id) ?: return Result.success()
        val url = item.url ?: return Result.success()
        return when (val r = fetcher.fetch(url, item.platform)) {
            is FetchResult.Ok -> { repo.applyDetails(id, r.details, forUrl = url); Result.success() }
            FetchResult.GiveUp -> { repo.markDetailsAttempted(id, forUrl = url); Result.success() }
            FetchResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else {
                // Leave detailsFetchedAt empty: the next app open tries again (risk table: fallback).
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val TAG = "details"
        const val MAX_ATTEMPTS = 4
    }
}

/** BRB-04 / WK-02: local notifications, decided on the phone; no push service. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LibraryRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.dailyReminder && !s.weeklySummary) return Result.success()
        val now = clock.instant().atZone(clock.zone)
        val rules = repo.observeRules().first()
        val pick = HomePicker().pick(repo.observeLibrary().first(), rules, now).firstOrNull()
        when (Reminders.decide(s.reminderState(), now, hasPick = pick != null)) {
            ReminderDecision.Daily -> if (pick != null) {
                val intents = repo.observeIntents().first()
                if (notifier.showPick(pick.item, intents)) settings.markDailySent(now.toLocalDate())
            }
            ReminderDecision.Weekly -> {
                val week = Weeks.weekStart(now.toLocalDate())
                val summary = repo.observeWeek(week).first()
                if (notifier.showWeekly(summary)) settings.markWeeklySent(now.toLocalDate(), week)
            }
            ReminderDecision.None -> Unit
        }
        return Result.success()
    }

    companion object {
        const val NAME = "reminders"
    }
}
