package app.caddis.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.caddis.data.LibraryRepository
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.analytics.AnalyticsSink
import app.caddis.domain.model.Collection
import app.caddis.domain.model.Intent
import app.caddis.domain.model.Item
import app.caddis.domain.schedule.TidyUp
import app.caddis.ui.ItemActions
import app.caddis.ui.Messenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class TidyState(
    val loaded: Boolean = false,
    val queue: List<Item> = emptyList(),
    val index: Int = 0,
    val kept: Int = 0,
    val deleted: Int = 0,
) {
    val current: Item? get() = queue.getOrNull(index)
    val finished: Boolean get() = loaded && index >= queue.size
}

/** Screen 19: review saves untouched for 90 days one at a time — keep or delete (FIN-03). */
@HiltViewModel
class TidyViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val actions: ItemActions,
    private val analytics: AnalyticsSink,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(TidyState())
    val state: StateFlow<TidyState> = _state.asStateFlow()
    val intents: StateFlow<List<Intent>> = repo.observeIntents().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Snapshot the queue once so the list doesn't shift under the person while reviewing.
        viewModelScope.launch {
            val q = TidyUp.candidates(repo.observeLibrary().first(), clock.instant())
            _state.value = TidyState(loaded = true, queue = q)
        }
    }

    fun keep() {
        val it = _state.value.current ?: return
        viewModelScope.launch { repo.keep(it.id) }
        advance(kept = 1)
    }

    fun delete() {
        val it = _state.value.current ?: return
        actions.delete(it, AnalyticsEvent.DeleteSource.TIDY_UP, toast = false)
        advance(deleted = 1)
    }

    fun openFirst() = _state.value.current?.let { actions.open(it, AnalyticsEvent.OpenEntry.HOME) }

    private fun advance(kept: Int = 0, deleted: Int = 0) {
        _state.update { it.copy(index = it.index + 1, kept = it.kept + kept, deleted = it.deleted + deleted) }
        val s = _state.value
        if (s.finished) analytics.log(AnalyticsEvent.TidyUpFinished(s.kept, s.deleted))
    }
}

/** Screen 20: restore or delete forever within 30 days (FIN-02). */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val messenger: Messenger,
    val clock: Clock,
) : ViewModel() {
    val items: StateFlow<List<Item>?> = repo.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(item: Item) = viewModelScope.launch {
        repo.restore(item.id)
        messenger.show("Restored to your Library.")
    }

    fun purge(item: Item) = viewModelScope.launch {
        repo.purge(item.id)
        messenger.show("Deleted for good.")
    }
}

/** Screen 14: create, rename, delete collections (LIB-02). */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val messenger: Messenger,
) : ViewModel() {
    val collections: StateFlow<List<Pair<Collection, Int>>> =
        combine(repo.observeCollections(), repo.observeLibrary()) { cs, items -> cs.map { c -> c to items.count { c.id in it.collectionIds } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val error = MutableStateFlow("")

    fun create(name: String, onOk: () -> Unit) = viewModelScope.launch {
        when (val r = repo.createCollection(name)) {
            is LibraryRepository.NameResult.Ok -> { error.value = ""; messenger.show("Created “${name.trim()}”."); onOk() }
            is LibraryRepository.NameResult.Error -> error.value = r.message
        }
    }

    fun rename(id: String, name: String, onOk: () -> Unit) = viewModelScope.launch {
        when (val r = repo.renameCollection(id, name)) {
            is LibraryRepository.NameResult.Ok -> { error.value = ""; messenger.show("Renamed."); onOk() }
            is LibraryRepository.NameResult.Error -> messenger.show(r.message)
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        error.value = ""
        repo.deleteCollection(id)
        messenger.show("Collection deleted. Its saves are still in your library.")
    }

    @Suppress("unused")
    val count = collections.map { it.size }
}
