package com.nexxlabs.caddis.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexxlabs.caddis.data.LibraryFeed
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.analytics.AnalyticsSink
import com.nexxlabs.caddis.domain.library.LibrarySections
import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.Platform
import com.nexxlabs.caddis.domain.platform.PlatformFacet
import com.nexxlabs.caddis.domain.platform.PlatformFacets
import com.nexxlabs.caddis.domain.search.SearchQuery
import com.nexxlabs.caddis.domain.search.resultBucket
import com.nexxlabs.caddis.ui.ItemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class StatusFilter(val label: String) { ACTIVE("Active"), DONE("Done"), ALL("All") }

/** SRC-04 / LIB-01/03: intent, platform, status and collection filters. */
data class Filters(
    val intentId: String? = null,
    val platform: Platform? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val collectionId: String? = null,
) {
    /**
     * What the Filter button counts: the choices that live inside the sheet. The Library's chip row
     * shows platforms itself ([platformInRow]), so that one is not counted there.
     */
    fun sheetCount(platformInRow: Boolean = false): Int =
        (if (intentId != null) 1 else 0) +
            (if (platform != null && !platformInRow) 1 else 0) +
            (if (status != StatusFilter.ALL) 1 else 0)

    fun apply(items: List<Item>): List<Item> = items.filter { i ->
        (intentId == null || i.intentId == intentId) &&
            (platform == null || i.platform == platform) &&
            (collectionId == null || collectionId in i.collectionIds) &&
            when (status) {
                StatusFilter.ACTIVE -> i.status == ItemStatus.ACTIVE
                StatusFilter.DONE -> i.status == ItemStatus.DONE
                StatusFilter.ALL -> true
            }
    }
}

data class SearchHit(val item: Item, val why: String)

data class LibraryState(
    /** False until Room has answered once. The screen shows placeholders then, never an empty message. */
    val loaded: Boolean = false,
    /** Everything the filters let through, finished or not. The shelf lists these; the Library splits them in [sections]. */
    val items: List<Item> = emptyList(),
    /** The same saves as the Library shows them: active first, then a Completed section (LIB-01). */
    val sections: LibrarySections = LibrarySections(emptyList(), emptyList()),
    val hits: List<SearchHit> = emptyList(),
    val intents: List<Intent> = emptyList(),
    val collections: List<Pair<Collection, Int>> = emptyList(),
    /** Platforms that have at least one save in scope, most saves first (LIB-03). Not a fixed list. */
    val platforms: List<PlatformFacet> = emptyList(),
    val filters: Filters = Filters(),
    val query: String = "",
    val searching: Boolean = false,
    val showCollections: Boolean = false,
    val recent: List<String> = emptyList(),
)

private data class Ui(
    val filters: Filters = Filters(),
    val query: String = "",
    val searching: Boolean = false,
    val showCollections: Boolean = false,
    val recent: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val feed: LibraryFeed,
    private val actions: ItemActions,
    private val analytics: AnalyticsSink,
    handle: SavedStateHandle,
) : ViewModel() {

    /** Set when this ViewModel backs a collection shelf (screen 12). */
    private val shelfCollection: String? = handle["collectionId"]

    private val ui = MutableStateFlow(
        Ui(filters = if (shelfCollection != null) Filters(status = StatusFilter.ACTIVE, collectionId = shelfCollection) else Filters()),
    )

    // Only a typed query waits for a pause; a blank one has nothing to look up, so it must not hold up the first state.
    // The empty start means the combine below is not waiting on search before it can show the library.
    private val searchResults = ui.map { it.query.trim() }.distinctUntilChanged()
        .debounce { q -> if (q.isEmpty()) 0L else 120L }
        .flatMapLatest { q -> repo.search(q) }
        .onStart { emit(emptyList()) }

    private fun build(items: List<Item>, found: List<Item>, intents: List<Intent>, collections: List<Collection>, u: Ui): LibraryState {
        val counts = collections.map { c -> c to items.count { c.id in it.collectionIds } }
        val q = u.query.trim()
        // Platforms come from the saves themselves; a collection shelf lists only its own.
        val inScope = u.filters.collectionId?.let { cid -> items.filter { cid in it.collectionIds } } ?: items
        val platforms = PlatformFacets.of(inScope)
        // The chosen platform's last save may be gone: then there is nothing left to filter by.
        val filters = u.filters.platform?.takeIf { p -> platforms.none { it.platform == p } }
            ?.let { u.filters.copy(platform = null) } ?: u.filters
        val shown = filters.apply(items)
        return LibraryState(
            loaded = true,
            items = shown,
            sections = LibrarySections.of(shown),
            hits = if (q.isEmpty()) emptyList() else filters.apply(found).map { SearchHit(it, SearchQuery.explain(it, q)) },
            intents = intents,
            collections = counts,
            platforms = platforms,
            filters = filters,
            query = u.query,
            searching = u.searching,
            showCollections = u.showCollections,
            recent = u.recent,
        )
    }

    /**
     * The feed is read at app start, so by the time this ViewModel exists it usually already has the library:
     * the first frame is the real one. If Room has not answered yet, the state is `loaded = false` and the
     * screen shows placeholders, never "Nothing here".
     */
    private fun firstState(): LibraryState {
        val items = feed.items.value
        val intents = feed.intents.value
        val collections = feed.collections.value
        return if (items != null && intents != null && collections != null) build(items, emptyList(), intents, collections, ui.value)
        else LibraryState(filters = ui.value.filters)
    }

    val state: StateFlow<LibraryState> = combine(
        feed.items.filterNotNull(), searchResults, feed.intents.filterNotNull(), feed.collections.filterNotNull(), ui,
    ) { items, found, intents, collections, u -> build(items, found, intents, collections, u) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), firstState())

    fun setIntentFilter(id: String?) = ui.update { it.copy(filters = it.filters.copy(intentId = if (it.filters.intentId == id) null else id)) }
    fun setPlatform(p: Platform?) = ui.update { it.copy(filters = it.filters.copy(platform = p)) }
    fun setStatus(s: StatusFilter) = ui.update { it.copy(filters = it.filters.copy(status = s)) }
    fun resetFilters() = ui.update {
        it.copy(filters = if (shelfCollection != null) Filters(status = StatusFilter.ACTIVE, collectionId = shelfCollection) else Filters())
    }

    fun showCollections(on: Boolean) = ui.update { it.copy(showCollections = on) }

    fun openSearch() = ui.update { it.copy(searching = true, query = "", filters = Filters()) }
    fun closeSearch() = ui.update { it.copy(searching = false, query = "", filters = Filters()) }
    fun setQuery(q: String) = ui.update { it.copy(query = q) }

    /** On submit: remember the words locally and log only the result-count bucket (never the words). */
    fun submitSearch() {
        val q = ui.value.query.trim()
        if (q.isEmpty()) return
        ui.update { it.copy(recent = (listOf(q) + it.recent.filter { r -> r != q }).take(4)) }
        analytics.log(AnalyticsEvent.SearchRun(resultBucket(state.value.hits.size)))
    }

    fun done(item: Item) = actions.done(item, state.value.intents)
    fun markActive(item: Item) = actions.markActive(item)
}
