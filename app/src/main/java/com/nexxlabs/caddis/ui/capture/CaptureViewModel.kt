package com.nexxlabs.caddis.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.data.SaveOutcome
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent.SaveEntry
import com.nexxlabs.caddis.domain.link.LinkCheck
import com.nexxlabs.caddis.domain.link.Links
import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CaptureSheet {
    data object None : CaptureSheet
    /** [offerNote]: what was typed is not a web link, so the sheet asks to save it as a note (CAP-05). */
    data class Add(
        val mode: Mode = Mode.LINK,
        val url: String = "",
        val error: String = "",
        val clipboard: String? = null,
        val offerNote: Boolean = false,
    ) : CaptureSheet {
        enum class Mode { LINK, NOTE }
    }
    data class Saved(val itemId: String, val entry: SaveEntry, val sharedFrom: String) : CaptureSheet
    data class Duplicate(val existing: Item, val link: LinkCheck.Valid, val entry: SaveEntry, val sourceApp: String, val titleHint: String) : CaptureSheet
}

/** Navigation requests out of capture (open a new note, open an earlier save). */
sealed interface CaptureNav {
    data class OpenNote(val id: String) : CaptureNav
    data class OpenItem(val id: String) : CaptureNav
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repo: LibraryRepository,
    /** Saves and their last edits run here so closing the sheet never drops them (NFR reliability). */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _sheet = MutableStateFlow<CaptureSheet>(CaptureSheet.None)
    val sheet: StateFlow<CaptureSheet> = _sheet.asStateFlow()

    private val _nav = MutableSharedFlow<CaptureNav>(extraBufferCapacity = 2)
    val nav = _nav.asSharedFlow()

    val intents: StateFlow<List<Intent>> = repo.observeIntents().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collections: StateFlow<List<Collection>> = repo.observeCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The save being confirmed; updates live as details load (MET-02). */
    val savedItem: StateFlow<Item?> = _sheet
        .flatMapLatest { s -> if (s is CaptureSheet.Saved) repo.observeItem(s.itemId) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val reasonDrafts = MutableStateFlow<Pair<String, String>?>(null)

    init {
        viewModelScope.launch {
            reasonDrafts.filterNotNull().debounce(400).collect { (id, r) -> repo.setReason(id, r) }
        }
    }

    // ---------- in-app Add (CAP-04/05) ----------

    /** [clipboard] is read by the UI only after the person tapped Add (CAP-04). */
    fun openAdd(clipboard: String?) {
        val suggestion = clipboard?.let { Links.extractFirst(it) ?: it.trim() }
            ?.takeIf { Links.check(it) is LinkCheck.Valid }
        _sheet.value = CaptureSheet.Add(clipboard = suggestion)
    }

    fun setAddMode(mode: CaptureSheet.Add.Mode) = _sheet.update { (it as? CaptureSheet.Add)?.copy(mode = mode, error = "", offerNote = false) ?: it }

    fun setAddUrl(url: String) = _sheet.update { (it as? CaptureSheet.Add)?.copy(url = url, error = "", offerNote = false) ?: it }

    fun submitLink(raw: String) = viewModelScope.launch {
        when (val c = Links.check(raw)) {
            is LinkCheck.Invalid -> _sheet.update { s ->
                val add = s as? CaptureSheet.Add ?: return@update s
                if (Links.couldBeNote(raw)) add.copy(url = raw, error = "", offerNote = true)
                else add.copy(url = raw, error = c.message, offerNote = false)
            }
            is LinkCheck.Valid -> save(c, SaveEntry.IN_APP, sourceApp = "", titleHint = "", sharedFrom = "")
        }
    }

    /** "Save as a note instead": the typed text becomes a note and the save sheet confirms it. */
    fun saveTypedAsNote() {
        val add = _sheet.value as? CaptureSheet.Add ?: return
        val text = add.url.trim()
        if (text.isEmpty()) return
        _sheet.value = CaptureSheet.None
        appScope.launch {
            val title = Links.noteTitleFrom(text)
            val item = repo.saveNote(title, body = if (text == title) "" else text)
            _sheet.value = CaptureSheet.Saved(item.id, SaveEntry.IN_APP, sharedFrom = "")
        }
    }

    fun submitNote(title: String) = viewModelScope.launch {
        val item = repo.saveNote(title)
        _sheet.value = CaptureSheet.None
        _nav.tryEmit(CaptureNav.OpenNote(item.id))
    }

    // ---------- share sheet (CAP-01/05/07) ----------

    /**
     * Saves whatever was shared, before anything else happens. A link becomes a save; plain text
     * without a link becomes a note (CAP-05). Returns false when there was nothing to save.
     */
    fun fromShare(text: String?, subject: String?, sourceApp: String): Boolean {
        val body = text?.trim().orEmpty()
        if (body.isEmpty() && subject.isNullOrBlank()) return false
        val found = Links.extractFirst(body) ?: body.takeIf { !it.contains(' ') && it.contains('.') }
        val check = found?.let { Links.check(it) } as? LinkCheck.Valid
        appScope.launch {
            if (check != null) {
                // Shared text around the link is often the post's title; keep it as a hint.
                val hint = (subject ?: body.replace(found, "")).trim().take(200)
                save(check, SaveEntry.SHARE_SHEET, sourceApp, hint, sharedFrom = sourceApp)
            } else {
                val title = subject?.takeIf { it.isNotBlank() } ?: body.lineSequence().first().take(80)
                val item = repo.saveNote(title, body, SaveEntry.SHARE_SHEET, sourceApp)
                _sheet.value = CaptureSheet.Saved(item.id, SaveEntry.SHARE_SHEET, sourceApp)
            }
        }
        return true
    }

    private suspend fun save(link: LinkCheck.Valid, entry: SaveEntry, sourceApp: String, titleHint: String, sharedFrom: String, allowDuplicate: Boolean = false) {
        when (val r = repo.saveLink(link, entry, sourceApp = sourceApp, titleHint = titleHint, allowDuplicate = allowDuplicate)) {
            // Only name a source we actually know; a link added in-app was not "shared from" its own platform.
            is SaveOutcome.Saved -> _sheet.value = CaptureSheet.Saved(r.item.id, entry, sharedFrom)
            is SaveOutcome.Duplicate -> _sheet.value = CaptureSheet.Duplicate(r.existing, r.link, entry, sourceApp, titleHint)
        }
    }

    // ---------- duplicate (CAP-06) ----------

    fun saveAgain() = viewModelScope.launch {
        val d = _sheet.value as? CaptureSheet.Duplicate ?: return@launch
        save(d.link, d.entry, d.sourceApp, d.titleHint, sharedFrom = d.sourceApp, allowDuplicate = true)
    }

    fun openEarlier() {
        val d = _sheet.value as? CaptureSheet.Duplicate ?: return
        _sheet.value = CaptureSheet.None
        _nav.tryEmit(CaptureNav.OpenItem(d.existing.id))
    }

    // ---------- save sheet edits ----------

    fun setIntent(itemId: String, intentId: String) = viewModelScope.launch { repo.setIntent(itemId, intentId) }

    fun setReason(itemId: String, reason: String) {
        reasonDrafts.value = itemId to reason
    }

    /** Runs on the app scope: the sheet is closing as this is called. */
    fun setTitle(itemId: String, title: String) = appScope.launch { repo.editTitle(itemId, title) }

    fun setFrom(itemId: String, from: String) = viewModelScope.launch { repo.setSourceFrom(itemId, from) }

    fun toggleCollection(itemId: String, collectionId: String, inIt: Boolean) =
        viewModelScope.launch { repo.setInCollection(itemId, collectionId, inIt) }

    fun createCollection(itemId: String, name: String) = viewModelScope.launch {
        val r = repo.createCollection(name)
        if (r is LibraryRepository.NameResult.Ok) repo.setInCollection(itemId, r.id, true)
    }

    /** CAP-02: dismissing keeps the save. Flushes a pending reason first. */
    fun dismiss(finalReason: Pair<String, String>? = null) {
        finalReason?.let { (id, r) -> appScope.launch { repo.setReason(id, r) } }
        _sheet.value = CaptureSheet.None
    }
}
