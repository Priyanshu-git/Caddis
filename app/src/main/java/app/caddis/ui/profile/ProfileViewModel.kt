package app.caddis.ui.profile

import android.content.Context
import android.content.Intent as AndroidIntent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.caddis.CaddisApp
import app.caddis.data.LibraryRepository
import app.caddis.data.Settings
import app.caddis.data.SettingsRepository
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.analytics.AnalyticsSink
import app.caddis.domain.model.Intent
import app.caddis.domain.model.IntentRule
import app.caddis.domain.schedule.QuietHours
import app.caddis.ui.Messenger
import coil3.SingletonImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class IntentRow(val intent: Intent, val count: Int)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    private val messenger: Messenger,
    private val analytics: AnalyticsSink,
    private val clock: Clock,
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
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

    fun cycleQuiet() = viewModelScope.launch {
        val q = settings.value?.quietHours ?: QuietHours.TEN_TO_SEVEN
        settingsRepo.setQuietHours(q.next())
    }

    fun finishOnboarding() = viewModelScope.launch { settingsRepo.setOnboarded() }

    // ---------- background work ----------

    fun setPauseBattery(on: Boolean) = viewModelScope.launch { settingsRepo.setPauseOnLowBattery(on) }
    fun setPauseData(on: Boolean) = viewModelScope.launch { settingsRepo.setPauseOnMobileData(on) }

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
