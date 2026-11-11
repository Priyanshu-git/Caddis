package com.nexxlabs.caddis.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.platform.PlatformFacets
import com.nexxlabs.caddis.ui.capture.Segmented
import com.nexxlabs.caddis.ui.components.BigTitle
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
import com.nexxlabs.caddis.ui.components.IconBtn
import com.nexxlabs.caddis.ui.components.ItemRow
import com.nexxlabs.caddis.ui.components.Tag
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

    // Search opens and closes with a fade rather than a cut.
    AnimatedContent(
        targetState = s.searching,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeIn(tween(Motion.Short, delayMillis = 60, easing = LinearEasing)) togetherWith fadeOut(tween(Motion.Quick, easing = LinearEasing)) },
        label = "librarySearch",
    ) { searching ->
        if (searching) {
            SearchView(s, vm, onOpenItem, onFilters = { filters = true })
        } else {
            Column(Modifier.fillMaxSize()) {
                BigTitle("Library") {
                    IconBtn(CIcons.Trash, "Trash", onTrash)
                }
                Row(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .border(1.dp, C.colors.line, RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button) { vm.openSearch() }
                        .semantics { contentDescription = "Search your saves" }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(CIcons.Search, null, tint = C.colors.fg2, modifier = Modifier.size(20.dp))
                    Text("Search your saves", style = CaddisType.body, color = C.colors.fg2)
                }
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
                            LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 100.dp)) {
                                item(key = "count") {
                                    val n = s.items.size
                                    val what = buildString {
                                        s.filters.intentId?.let { append(" for ", intentName(s.intents, it)) }
                                        s.filters.platform?.let { append(" from ", it.label) }
                                        if (isEmpty()) append(" and notes")
                                    }
                                    Fine("$n ${if (n == 1) "save" else "saves"}$what · newest first", Modifier.padding(top = 6.dp))
                                }
                                if (s.items.isEmpty()) {
                                    item(key = "empty") { EmptyState(null, "Nothing matches these filters.", { CButton("Clear filters", onClick = vm::resetFilters, small = true) }, rings = false) }
                                }
                                items(s.items, key = { it.id }) { i ->
                                    ItemRow(
                                        i, i.meta() + " · " + i.savedLabel(), onClick = { onOpenItem(i) },
                                        right = { Tag(i.rightTag(s.intents) ?: intentName(s.intents, i.intentId)) },
                                        modifier = calmItem(), share = "library",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filters) FilterSheet(s, vm, onDismiss = { filters = false })
}

/** Platforms the library has, as scrolling chips. Intent and status live in the Filter sheet. */
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

/** Screens 17–18: search on the phone (SRC-01/02/03). */
@Composable
private fun SearchView(s: LibraryState, vm: LibraryViewModel, onOpenItem: (Item) -> Unit, onFilters: () -> Unit) {
    val focus = remember { FocusRequester() }
    BackHandler { vm.closeSearch() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 46.dp).border(1.dp, C.colors.line, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(CIcons.Search, null, tint = C.colors.fg2, modifier = Modifier.size(20.dp))
                CField(s.query, vm::setQuery, "Search your saves", label = "Search saves", boxed = false, imeAction = ImeAction.Search, onIme = vm::submitSearch, modifier = Modifier.weight(1f).focusRequester(focus))
            }
            CButton("Cancel", onClick = vm::closeSearch, style = BtnStyle.Ghost, small = true)
        }
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
                    ItemRow(h.item, h.item.meta(), onClick = { onOpenItem(h.item) }, why = h.why, right = h.item.rightTag(s.intents)?.let { t -> { Tag(t) } })
                }
            }
        }
    }
}

/** Screen 13: filters for intent, platform and status (SRC-04, LIB-03). */
@Composable
fun FilterSheet(s: LibraryState, vm: LibraryViewModel, onDismiss: () -> Unit) {
    val count = if (s.searching && s.query.isNotBlank()) s.hits.size else s.items.size
    CSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filter", style = CaddisType.sheetTitle, color = C.colors.fg, modifier = Modifier.weight(1f))
            CButton("Reset", onClick = vm::resetFilters, style = BtnStyle.Ghost, small = true)
        }
        FilterGroup("Intent", s.intents.map { it.name to (s.filters.intentId == it.id) }) { idx -> vm.setIntentFilter(s.intents[idx].id) }
        PlatformGroup(s, vm)
        FilterGroup("Status", StatusFilter.entries.map { it.label to (s.filters.status == it) }) { idx -> vm.setStatus(StatusFilter.entries[idx]) }
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
