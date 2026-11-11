package com.nexxlabs.caddis.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.details.DetailEdits
import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ReturnRule
import com.nexxlabs.caddis.ui.ItemActions
import com.nexxlabs.caddis.ui.Messenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val actions: ItemActions,
    private val messenger: Messenger,
    private val appScope: CoroutineScope,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String = checkNotNull(handle["id"])

    val item: StateFlow<Item?> = repo.observeItem(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val intents: StateFlow<List<Intent>> = repo.observeIntents().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collections: StateFlow<List<Collection>> = repo.observeCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Notes save as you type (prototype: "Saving…" → "Saved"). */
    private val noteDrafts = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            noteDrafts.filterNotNull().debounce(500).collect { md ->
                repo.setNote(id, md)
                saving.value = false
            }
        }
    }

    fun noteChanged(markdown: String) {
        if (markdown == item.value?.note) return
        saving.value = true
        noteDrafts.value = markdown
    }

    /** Called when leaving the editor so a last keystroke is never lost. */
    fun flushNote() {
        val md = noteDrafts.value ?: return
        if (md != item.value?.note) appScope.launch { repo.setNote(id, md) }
    }

    fun setTitle(t: String) = appScope.launch { repo.setTitle(id, t) }
    /**
     * Saves the Edit details sheet. Runs on the app scope so closing the screen never drops it.
     * Returns a message to show on the sheet when the link cannot be used, or null when saved.
     */
    suspend fun saveDetails(url: String?, e: DetailEdits): String? =
        when (val r = appScope.async { repo.saveDetails(id, url, e) }.await()) {
            is LibraryRepository.DetailsResult.BadLink -> r.message
            is LibraryRepository.DetailsResult.LinkTaken -> "Another save already has this link: “${r.other.displayTitle}”."
            is LibraryRepository.DetailsResult.Saved -> {
                if (r.refetching) messenger.show("Link changed. Fetching its details.")
                null
            }
        }
    fun setIntent(intentId: String) = viewModelScope.launch { repo.setIntent(id, intentId) }
    fun setReason(r: String) = viewModelScope.launch { repo.setReason(id, r) }
    fun toggleCollection(cid: String, on: Boolean) = viewModelScope.launch { repo.setInCollection(id, cid, on) }

    fun open(entry: AnalyticsEvent.OpenEntry = AnalyticsEvent.OpenEntry.LIBRARY) = item.value?.let { actions.open(it, entry) }
    fun share() = item.value?.let { actions.share(it) }
    fun done() =item.value?.let { actions.done(it, intents.value) }
    fun markActive() = item.value?.let { actions.markActive(it) }
    fun delete() = item.value?.let { actions.delete(it) }
    fun later(rule: ReturnRule, date: LocalDate?) = item.value?.let { actions.later(it, rule, date) }
    fun today() = actions.today()

    fun refresh() = viewModelScope.launch {
        val it = item.value ?: return@launch
        repo.refreshDetails(id)
        messenger.show("Refreshing details. Your edits are kept.")
    }
}
