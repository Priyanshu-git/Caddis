package com.nexxlabs.caddis.ui.settings

import android.content.Context
import android.content.Intent as AndroidIntent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import com.nexxlabs.caddis.update.AppUpdater
import com.nexxlabs.caddis.update.UpdateState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexxlabs.caddis.CaddisApp
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.data.Settings
import com.nexxlabs.caddis.data.SettingsRepository
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.analytics.AnalyticsSink
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.IntentRule
import com.nexxlabs.caddis.ui.Messenger
import coil3.SingletonImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class IntentRow(val intent: Intent, val count: Int)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    private val messenger: Messenger,
    private val analytics: AnalyticsSink,
    private val clock: Clock,
    private val updater: AppUpdater,
) : ViewModel() {

    /** A newer Caddis on Google Play, for the Settings row. */
    val update: StateFlow<UpdateState> = updater.state

    fun startUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) = updater.start(launcher)
    fun restartToUpdate() = updater.restart()

    /** A short message for the toast line, for screens that have no ViewModel of their own to speak through. */
    fun tell(message: String) = messenger.show(message)

    /** Where "Check for updates" stands. Only [UpToDate] means Google Play actually answered. */
    enum class CheckStatus { Idle, Checking, UpToDate, Unreachable }

    val checkStatus = MutableStateFlow(CheckStatus.Idle)

    /**
     * "Check for updates": asks Play now. An update that is waiting opens Play's dialog, a downloaded one restarts the app,
     * and otherwise [checkStatus] says what happened, so the row can show it in place. It never says "up to date" unless Play answered.
     */
    fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (checkStatus.value == CheckStatus.Checking) return
        checkStatus.value = CheckStatus.Checking
        viewModelScope.launch {
            var result = CheckStatus.Idle
            try {
                if (!updater.refresh()) {
                    result = CheckStatus.Unreachable
                } else {
                    when (updater.state.value) {
                        is UpdateState.Available -> updater.start(launcher)
                        UpdateState.Ready -> updater.restart()
                        UpdateState.Downloading -> Unit
                        UpdateState.None -> result = CheckStatus.UpToDate
                    }
                }
            } finally {
                checkStatus.value = result
            }
        }
    }

    val settings: StateFlow<Settings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** How many saves are in Trash, for the Your data rows. */
    val trashCount: StateFlow<Int> = repo.observeTrash().map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val rules: StateFlow<Map<String, IntentRule>> = repo.observeRules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val intents: StateFlow<List<IntentRow>> = combine(repo.observeIntents(), repo.observeLibrary()) { ints, items ->
        ints.map { i -> IntentRow(i, items.count { it.intentId == i.id }) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cacheBytes = MutableStateFlow(0L)
    val intentError = MutableStateFlow("")
    val newIntentError = MutableStateFlow("")

    // ---------- notifications (BRB-04, WK-02) ----------

    fun setDaily(on: Boolean) = viewModelScope.launch {
        settingsRepo.setDailyReminder(on)
        analytics.log(AnalyticsEvent.NotificationSetting("daily_reminder", on))
    }

    fun setWeekly(on: Boolean) = viewModelScope.launch {
        settingsRepo.setWeeklySummary(on)
        analytics.log(AnalyticsEvent.NotificationSetting("weekly_summary", on))
    }

    /** Ends first launch. [skippedAtStep] is the intro card Skip was tapped on, or null when the person went through. */
    fun finishOnboarding(skippedAtStep: Int? = null) = viewModelScope.launch {
        val s = settingsRepo.current()
        analytics.log(AnalyticsEvent.OnboardingFinished(skippedAtStep, s.dailyReminder, s.weeklySummary))
        settingsRepo.setOnboarded()
    }

    // ---------- background work ----------

    fun setPauseBattery(on: Boolean) = viewModelScope.launch { settingsRepo.setPauseOnLowBattery(on) }

    // ---------- intents and rules (INT-04/05) ----------

    fun updateRule(rule: IntentRule) = viewModelScope.launch { repo.updateRule(rule) }

    fun createIntent(name: String, done: String, onOk: () -> Unit) = viewModelScope.launch {
        when (val r = repo.createIntent(name, done)) {
            is LibraryRepository.NameResult.Ok -> { newIntentError.value = ""; messenger.show("Created “${name.trim()}”."); onOk() }
            is LibraryRepository.NameResult.Error -> newIntentError.value = r.message
        }
    }

    fun updateIntent(id: String, name: String, done: String, onOk: () -> Unit) = viewModelScope.launch {
        when (val r = repo.updateIntent(id, name, done)) {
            is LibraryRepository.NameResult.Ok -> { intentError.value = ""; messenger.show("Saved."); onOk() }
            is LibraryRepository.NameResult.Error -> intentError.value = r.message
        }
    }

    fun deleteIntent(id: String, onOk: () -> Unit) = viewModelScope.launch {
        val n = repo.deleteIntent(id)
        messenger.show("Intent deleted. $n ${if (n == 1) "save" else "saves"} moved to Just keep.")
        onOk()
    }

    // ---------- your data (ACC-02, MET-04) ----------

    fun refreshCache() = viewModelScope.launch {
        cacheBytes.value = withContext(Dispatchers.IO) { dirSize(File(context.cacheDir, CaddisApp.THUMB_CACHE_DIR)) }
    }

    fun clearCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        refreshCache()
        messenger.show("Thumbnail cache cleared.")
    }

    /** Builds the export from Room (tech arch 1.1) and hands it to the system share sheet. */
    fun export(json: Boolean, launch: (AndroidIntent) -> Unit) = viewModelScope.launch {
        val (text, ext, mime) = if (json) Triple(repo.exportJson(), "json", "application/json") else Triple(repo.exportHtml(), "html", "text/html")
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(dir, "caddis-${LocalDate.now(clock)}.$ext").apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = AndroidIntent(AndroidIntent.ACTION_SEND).setType(mime).putExtra(AndroidIntent.EXTRA_STREAM, uri)
            .addFlags(AndroidIntent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(AndroidIntent.createChooser(send, if (json) "Export library (JSON)" else "Export bookmarks (HTML)"))
    }

    /** ACC-03, client-only phase: delete everything on this phone. */
    fun deleteAll(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteAllData()
        settingsRepo.reset()
        clearCache()
        messenger.show("All your Caddis data on this phone was deleted.")
        onDone()
    }

    private fun dirSize(f: File): Long = if (!f.exists()) 0 else f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
