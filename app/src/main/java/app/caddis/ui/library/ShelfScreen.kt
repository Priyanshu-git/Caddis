package app.caddis.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemStatus
import app.caddis.ui.components.CButton
import app.caddis.ui.components.CChip
import app.caddis.ui.components.CIcons
import app.caddis.ui.components.EmptyState
import app.caddis.ui.components.IconBtn
import app.caddis.ui.components.ItemRow
import app.caddis.ui.components.Sub
import app.caddis.ui.components.Tag
import app.caddis.ui.calmItem
import app.caddis.ui.meta
import app.caddis.ui.rightTag
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType

/** Screen 12: one collection's saves, with Active / Done / All. */
@Composable
fun ShelfScreen(onBack: () -> Unit, onOpenItem: (Item) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var filters by remember { mutableStateOf(false) }
    val title = s.collections.firstOrNull { it.first.id == s.filters.collectionId }?.first?.name ?: "Collection"
    val nf = (if (s.filters.platform != null) 1 else 0) + (if (s.filters.intentId != null) 1 else 0)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBtn(CIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(title, style = CaddisType.h1.copy(fontSize = CaddisType.itemTitle.fontSize * 1.15f), color = C.colors.fg)
                val due = s.items.count { it.status == ItemStatus.ACTIVE && it.returnAt != null }
                Sub("${s.items.size} ${if (s.items.size == 1) "save" else "saves"}" + if (due > 0) " · $due due" else "")
            }
            CChip("Filter" + if (nf > 0) " · $nf" else "", selected = nf > 0, onClick = { filters = true })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StatusFilter.entries) { st -> CChip(st.label, s.filters.status == st, onClick = { vm.setStatus(st) }) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            if (s.items.isEmpty()) item(key = "empty") { EmptyState(null, "Nothing here yet.", { CButton("Clear filters", onClick = vm::resetFilters, small = true) }, rings = false) }
            items(s.items, key = { it.id }) { i ->
                ItemRow(i, i.meta(), onClick = { onOpenItem(i) }, right = i.rightTag(s.intents)?.let { t -> { Tag(t) } }, modifier = calmItem(), share = "shelf")
            }
        }
    }
    if (filters) FilterSheet(s, vm, onDismiss = { filters = false })
}
