package com.nexxlabs.caddis.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.calmItem
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.platform.PlatformFacets
import com.nexxlabs.caddis.ui.capture.Segmented
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.CField
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.CSheet
import com.nexxlabs.caddis.ui.components.Chevron
import com.nexxlabs.caddis.ui.components.Chips
import com.nexxlabs.caddis.ui.components.EmptyState
import com.nexxlabs.caddis.ui.components.Eyebrow
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.PlaceholderRows
import com.nexxlabs.caddis.ui.components.IconBtn
import com.nexxlabs.caddis.ui.components.ItemRow
import com.nexxlabs.caddis.ui.components.Tag
import com.nexxlabs.caddis.ui.completedMeta
import com.nexxlabs.caddis.ui.intentName
import com.nexxlabs.caddis.ui.meta
import com.nexxlabs.caddis.ui.rightTag
import com.nexxlabs.caddis.ui.savedLabel
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType

/** Screens 11, 17, 18: Library with search, intent chips, filters and the Collections switch. */
@Composable
fun LibraryScreen(
    onOpenItem: (Item) -> Unit,
    onTrash: () -> Unit,
    onCollection: (String) -> Unit,
    onManageCollections: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var filters by remember { mutableStateOf(false) }
    // Completed starts collapsed. The back stack entry keeps these, so it stays as you left it while you read a save and come back.
    var completedOpen by rememberSaveable { mutableStateOf(false) }
    var showAllCompleted by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SearchHeader(s.searching, s.query, vm::setQuery, vm::submitSearch, vm::openSearch, vm::closeSearch, onTrash)
        // The body swaps between the library and the search results with a fade rather than a cut.
        AnimatedContent(
            targetState = s.searching,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(Motion.Short, delayMillis = 60, easing = LinearEasing)) togetherWith fadeOut(tween(Motion.Quick, easing = LinearEasing)) },
            label = "librarySearch",
        ) { searching ->
            if (searching) {
                SearchBody(s, vm, onOpenItem, onFilters = { filters = true })
            } else {
                Column(Modifier.fillMaxSize()) {
                    Segmented(
                        listOf("All saves", "Collections"), if (s.showCollections) 1 else 0, { vm.showCollections(it == 1) }, "Library view",
                        Modifier.padding(horizontal = 18.dp).padding(bottom = 0.dp),
                    )
                    AnimatedContent(
                        targetState = s.showCollections,
                        modifier = Modifier.weight(1f),
                        transitionSpec = { fadeIn(tween(Motion.Short, delayMillis = Motion.Out, easing = LinearEasing)) togetherWith fadeOut(tween(Motion.Out, easing = LinearEasing)) },
                        label = "libraryView",
                    ) { showCollections ->
                        if (showCollections) {
                            LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 100.dp)) {
                                items(s.collections, key = { it.first.id }) { (c, n) -> Box(calmItem()) { CollectionRow(c.name, n) { onCollection(c.id) } } }
                                item(key = "manage") { CButton("Manage collections", onClick = onManageCollections, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) }
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                PlatformChipRow(s, vm, onFilters = { filters = true })
                                val sec = s.sections
                                LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 100.dp)) {
                                    // Room has not answered yet: placeholder rows (after a short grace), never "Nothing here".
                                    if (!s.loaded) {
                                        item(key = "loading") { PlaceholderRows(modifier = calmItem()) }
                                        return@LazyColumn
                                    }
                                    item(key = "count") {
                                        val n = sec.active.size
                                        val what = buildString {
                                            s.filters.intentId?.let { append(" for ", intentName(s.intents, it)) }
                                            s.filters.platform?.let { append(" from ", it.label) }
                                            if (isEmpty()) append(" and notes")
                                        }
                                        Fine("$n ${if (n == 1) "save" else "saves"}$what · newest first", Modifier.padding(top = 6.dp))
                                    }
                                    if (sec.active.isEmpty() && sec.completed.isEmpty()) {
                                        item(key = "empty") { EmptyState(null, "Nothing matches these filters.", { CButton("Clear filters", onClick = vm::resetFilters, small = true) }, rings = false) }
                                    } else if (sec.active.isEmpty()) {
                                        item(key = "nothingActive") { Fine("Nothing waiting. What you finished is below.", Modifier.padding(vertical = 14.dp)) }
                                    }
                                    items(sec.active, key = { it.id }) { i ->
                                        ItemRow(
                                            i, i.meta() + " · " + i.savedLabel(), onClick = { onOpenItem(i) },
                                            right = { Tag(i.rightTag(s.intents) ?: intentName(s.intents, i.intentId)) },
                                            modifier = calmItem(), share = "library",
                                        )
                                    }
                                    if (sec.completed.isNotEmpty()) {
                                        item(key = "completedHeader") {
                                            CompletedHeader(sec.completed.size, completedOpen) {
                                                completedOpen = !completedOpen
                                                if (!completedOpen) showAllCompleted = false
                                            }
                                        }
                                        if (completedOpen) {
                                            items(sec.shownCompleted(showAllCompleted), key = { it.id }) { i ->
                                                ItemRow(
                                                    i, i.completedMeta(s.intents), onClick = { onOpenItem(i) },
                                                    completed = true, modifier = calmItem(), share = "library",
                                                )
                                            }
                                            if (sec.hiddenCompleted(showAllCompleted) > 0) {
                                                item(key = "completedMore") { ShowAllCompleted(sec.completed.size) { showAllCompleted = true } }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filters) FilterSheet(s, vm, onDismiss = { filters = false }, showStatus = false)
}

private val FieldHeight = 46.dp

/**
 * The Library header. At rest it is the title with a search icon and the Trash button. Opening search grows
 * the icon leftwards into a full-width field (the title and Trash fade out, the icon turns into a back arrow),
 * so the page needs no permanent search box.
 */
@Composable
private fun SearchHeader(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTrash: () -> Unit,
) {
    val c = C.colors
    val p by animateFloatAsState(if (searching) 1f else 0f, tween(Motion.Medium, easing = Motion.Decelerate), label = "searchOpen")
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = searching, onBack = onClose)
    LaunchedEffect(searching) { if (searching) focus.requestFocus() else focusManager.clearFocus() }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).heightIn(min = 52.dp)) {
        val titleAlpha = (1f - p / 0.4f).coerceIn(0f, 1f)
        val contentAlpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
        Text("Library", style = CaddisType.h1, color = c.fg, modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).graphicsLayer { alpha = titleAlpha })
        if (p < 1f) {
            IconBtn(CIcons.Trash, "Trash", onTrash, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).graphicsLayer { alpha = 1f - p })
        }
        // Collapsed: a 46 dp circle just left of Trash. Open: from 10 dp to 10 dp of the screen width.
        val closedX = maxWidth - 54.dp - FieldHeight
        val x = lerp(closedX, 10.dp, p)
        val w = lerp(FieldHeight, maxWidth - 20.dp, p)
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = x)
                .size(width = w, height = FieldHeight)
                .border(1.dp, c.line.copy(alpha = c.line.alpha * contentAlpha), RoundedCornerShape(FieldHeight / 2))
                .clip(RoundedCornerShape(FieldHeight / 2)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(FieldHeight)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { if (searching) onClose() else onOpen() }
                    .semantics { contentDescription = if (searching) "Close search" else "Search your saves" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(CIcons.Search, null, tint = c.fg, modifier = Modifier.size(22.dp).graphicsLayer { alpha = 1f - p })
                Icon(CIcons.Back, null, tint = c.fg, modifier = Modifier.size(22.dp).graphicsLayer { alpha = p })
            }
            if (searching || p > 0f) {
                CField(
                    query, onQuery, "Search your saves", label = "Search saves", boxed = false,
                    imeAction = ImeAction.Search, onIme = onSubmit,
                    modifier = Modifier.weight(1f).focusRequester(focus).graphicsLayer { alpha = contentAlpha },
                )
                if (query.isNotEmpty()) IconBtn(CIcons.Close, "Clear search", { onQuery("") }, size = 18.dp)
                else Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/** The platforms the library has, as scrolling chips. Intents (Just keep included) live in the Filter sheet. */
@Composable
private fun PlatformChipRow(s: LibraryState, vm: LibraryViewModel, onFilters: () -> Unit) {
    val n = s.filters.sheetCount(platformInRow = true)
    LazyRow(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { CChip("Filter" + if (n > 0) " · $n" else "", selected = n > 0, onClick = onFilters, leading = CIcons.Filter, contentDescription = "More filters") }
        item { CChip("All", selected = s.filters.platform == null, onClick = { vm.setPlatform(null) }) }
        items(s.platforms, key = { it.platform.id }) { f ->
            val on = s.filters.platform == f.platform
            CChip(f.platform.label, selected = on, onClick = { vm.setPlatform(if (on) null else f.platform) })
        }
    }
}

/** The one quiet row that opens and closes the Completed section: "COMPLETED · 12" and Show / Hide. */
@Composable
private fun CompletedHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    val c = C.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = 8.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = "Completed, $count ${if (count == 1) "save" else "saves"}"
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("COMPLETED · $count", style = CaddisType.eyebrow, color = c.fg2, modifier = Modifier.weight(1f))
        Text(if (open) "Hide" else "Show", style = CaddisType.sub, color = c.accent)
        Icon(if (open) CIcons.Down else CIcons.Chevron, null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = c.line)
}

/** Under the latest few completed saves: the rest, in place. */
@Composable
private fun ShowAllCompleted(total: Int, onClick: () -> Unit) {
    val c = C.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Show all $total", style = CaddisType.strong, color = c.accent, modifier = Modifier.weight(1f))
        Icon(CIcons.Chevron, null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CollectionRow(name: String, count: Int, onClick: () -> Unit) {
    val c = C.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollectionBox()
            Column(Modifier.weight(1f)) {
                Text(name, style = CaddisType.strong, color = c.fg)
                Fine("$count ${if (count == 1) "save" else "saves"}")
            }
            Chevron()
        }
        HorizontalDivider(color = c.line)
    }
}

@Composable
fun CollectionBox() {
    val c = C.colors
    androidx.compose.foundation.layout.Box(
        Modifier.size(46.dp).border(1.dp, c.line, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(CIcons.Library, null, tint = c.fg2, modifier = Modifier.size(22.dp)) }
}

/** Screens 17-18: search results on the phone (SRC-01/02/03). The field lives in [SearchHeader]. */
@Composable
private fun SearchBody(s: LibraryState, vm: LibraryViewModel, onOpenItem: (Item) -> Unit, onFilters: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 100.dp)) {
            if (s.query.isBlank()) {
                if (s.recent.isNotEmpty()) {
                    item { Eyebrow("Recent", Modifier.padding(top = 8.dp, bottom = 8.dp)) }
                    items(s.recent) { r ->
                        Column {
                            Text(r, style = CaddisType.bodyLarge, color = C.colors.fg, modifier = Modifier.fillMaxWidth().clickable { vm.setQuery(r) }.heightIn(min = 48.dp).padding(vertical = 13.dp))
                            HorizontalDivider(color = C.colors.line)
                        }
                    }
                }
                item { Fine("Search matches the words you type in titles, notes, creators and links. It runs on your phone, so it works offline.", Modifier.padding(top = 18.dp)) }
            } else {
                item {
                    Chips(Modifier.padding(top = 6.dp, bottom = 10.dp)) { CChip("Filter" + if (s.filters.sheetCount() > 0) " · ${s.filters.sheetCount()}" else "", s.filters.sheetCount() > 0, onFilters) }
                    Fine("${s.hits.size} ${if (s.hits.size == 1) "result" else "results"}", Modifier.padding(bottom = 4.dp))
                }
                if (s.hits.isEmpty()) item { EmptyState(null, "Nothing matches. Try fewer words.", rings = false) }
                items(s.hits, key = { it.item.id }) { h ->
                    // A completed save keeps its look and carries the tick, so it sits among the other matches by relevance.
                    if (h.item.status == ItemStatus.DONE) {
                        ItemRow(h.item, h.item.completedMeta(s.intents), onClick = { onOpenItem(h.item) }, why = h.why, completed = true)
                    } else {
                        ItemRow(h.item, h.item.meta(), onClick = { onOpenItem(h.item) }, why = h.why, right = h.item.rightTag(s.intents)?.let { t -> { Tag(t) } })
                    }
                }
            }
        }
    }
}

/**
 * Screen 13: filters for intent, platform and status (SRC-04, LIB-03). The Library passes [showStatus] = false: its
 * Completed section already says which saves are finished, so a Status choice would only fight with it.
 */
@Composable
fun FilterSheet(s: LibraryState, vm: LibraryViewModel, onDismiss: () -> Unit, showStatus: Boolean = true) {
    val count = if (s.searching && s.query.isNotBlank()) s.hits.size else s.items.size
    CSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filter", style = CaddisType.sheetTitle, color = C.colors.fg, modifier = Modifier.weight(1f))
            CButton("Reset", onClick = vm::resetFilters, style = BtnStyle.Ghost, small = true)
        }
        FilterGroup("Intent", s.intents.map { it.name to (s.filters.intentId == it.id) }) { idx -> vm.setIntentFilter(s.intents[idx].id) }
        PlatformGroup(s, vm)
        if (showStatus) FilterGroup("Status", StatusFilter.entries.map { it.label to (s.filters.status == it) }) { idx -> vm.setStatus(StatusFilter.entries[idx]) }
        CButton("Show $count ${if (count == 1) "save" else "saves"}", onClick = onDismiss, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
    }
}

/** Only platforms the library actually has; nothing to choose from, nothing shown. */
@Composable
private fun PlatformGroup(s: LibraryState, vm: LibraryViewModel) {
    if (s.platforms.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = PlatformFacets.visible(s.platforms, s.filters.platform, expanded)
    val hidden = s.platforms.size - shown.size
    Eyebrow("Platform", Modifier.padding(top = 14.dp, bottom = 6.dp))
    Chips {
        CChip("All", s.filters.platform == null, onClick = { vm.setPlatform(null) })
        shown.forEach { f -> CChip(f.platform.label, s.filters.platform == f.platform, onClick = { vm.setPlatform(f.platform) }) }
        if (hidden > 0) CChip("More · $hidden", onClick = { expanded = true })
    }
}

@Composable
private fun FilterGroup(label: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Eyebrow(label, Modifier.padding(top = 14.dp, bottom = 6.dp))
    Chips { options.forEachIndexed { i, (name, on) -> CChip(name, on, onClick = { onPick(i) }) } }
}

@Suppress("unused")
private fun List<Intent>.nameOf(id: String) = firstOrNull { it.id == id }?.name
