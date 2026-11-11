package com.nexxlabs.caddis.ui.manage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.calmItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexxlabs.caddis.domain.schedule.Trash
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CCard
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.CField
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.EmptyState
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.IconBtn
import com.nexxlabs.caddis.ui.components.Sub
import com.nexxlabs.caddis.ui.components.Tag
import com.nexxlabs.caddis.ui.components.Thumb
import com.nexxlabs.caddis.ui.components.TopBar
import com.nexxlabs.caddis.ui.intentName
import com.nexxlabs.caddis.ui.library.CollectionBox
import com.nexxlabs.caddis.ui.meta
import com.nexxlabs.caddis.ui.sheets.rememberConfirm
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import java.time.Duration

@Composable
fun TidyScreen(onBack: () -> Unit, onLibrary: () -> Unit, vm: TidyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        val cur = s.current
        TopBar("Tidy-up", onBack) { if (cur != null) Fine("${s.index + 1} of ${s.queue.size}", Modifier.padding(end = 10.dp)) }
        if (!s.loaded) return@Column
        if (cur == null) {
            EmptyState("All tidy", "${s.kept} kept, ${s.deleted} deleted.", { CButton("Back to library", onClick = onLibrary, style = BtnStyle.Primary) })
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(Modifier.padding(top = 6.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.queue.indices.forEach { k ->
                    val segment by animateColorAsState(if (k <= s.index) c.accent else c.line, tween(Motion.Short), label = "tidySegment")
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(segment),
                    )
                }
            }
            // Keep / Delete moves to the next save with a fade, not a swap.
            AnimatedContent(
                targetState = cur,
                contentKey = { it.id },
                transitionSpec = { fadeIn(tween(Motion.Short, delayMillis = 60, easing = LinearEasing)) togetherWith fadeOut(tween(Motion.Quick, easing = LinearEasing)) },
                label = "tidyCard",
            ) { card ->
                CCard(Modifier.fillMaxWidth()) {
                    Thumb(card, size = 1000.dp, height = 130.dp, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.padding(top = 14.dp, bottom = 6.dp)) { Tag(intentName(intents, card.intentId)) }
                    Text(card.displayTitle, style = CaddisType.sheetTitle.copy(fontSize = CaddisType.sheetTitle.fontSize * 1.05f), color = c.fg)
                    Sub(card.meta())
                    val months = (Duration.between(card.createdAt, java.time.Instant.now()).toDays() / 30).coerceAtLeast(3)
                    Fine("Saved $months months ago · " + if (card.lastOpenedAt == null) "never opened" else "not opened lately", Modifier.padding(top = 8.dp))
                    if (card.reason.isNotBlank()) Text("“${card.reason}”", style = CaddisType.body.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = c.fg, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CButton("Keep", onClick = vm::keep, modifier = Modifier.weight(1f))
                CButton("Delete", onClick = vm::delete, modifier = Modifier.weight(1f))
            }
            if (cur.url != null) CButton("Open it first", onClick = { vm.openFirst() }, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Fine("Untouched for 90 days. Deleted saves stay in Trash for 30 days, so you can undo it.", Modifier.fillMaxWidth().padding(top = 14.dp), align = TextAlign.Center)
        }
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val confirm = rememberConfirm()
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Trash", onBack)
        Fine("Removed for good after 30 days.", Modifier.padding(start = 70.dp, end = 18.dp, bottom = 4.dp))
        val list = items ?: return@Column
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            if (list.isEmpty()) item(key = "empty") { EmptyState(null, "Nothing here. Deleted saves stay here for 30 days.", rings = false) }
            items(list, key = { it.id }) { i ->
                Column(calmItem()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumb(i, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(i.displayTitle, style = CaddisType.strong, color = c.fg, maxLines = 2)
                            val left = i.deletedAt?.let { Trash.daysLeft(it, vm.clock.instant()) } ?: 30
                            Fine("$left ${if (left == 1) "day" else "days"} left")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            CChip("Restore", onClick = { vm.restore(i) })
                            val armed = confirm.isArmed(i.id)
                            Text(
                                if (armed) "Sure? Tap again" else "Delete forever",
                                style = CaddisType.fine.copy(fontWeight = if (armed) androidx.compose.ui.text.font.FontWeight.SemiBold else null),
                                color = if (armed) c.crit else c.fg2,
                                modifier = Modifier.clickable { confirm.tap(i.id) { vm.purge(i) } }.heightIn(min = 30.dp).padding(top = 8.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = c.line)
                }
            }
            if (list.isNotEmpty()) item(key = "note") { Fine("Restored saves go back to your Library.", Modifier.padding(top = 14.dp)) }
        }
    }
}

@Composable
fun CollectionsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: CollectionsViewModel = hiltViewModel()) {
    val cols by vm.collections.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    val confirm = rememberConfirm()
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Collections", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            items(cols, key = { it.first.id }) { (col, n) ->
                Column(calmItem()) {
                    if (editing == col.id) {
                        var draft by remember(col.id) { mutableStateOf(col.name) }
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CField(draft, { draft = it }, "Collection name", maxLength = 40, modifier = Modifier.weight(1f))
                            CButton("Save", onClick = { vm.rename(col.id, draft) { editing = null } }, style = BtnStyle.Primary, small = true)
                            CButton(
                                if (confirm.isArmed(col.id)) "Sure? Saves stay" else "Delete",
                                onClick = { confirm.tap(col.id) { vm.delete(col.id); editing = null } },
                                style = BtnStyle.Danger, small = true,
                            )
                        }
                    } else {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).heightIn(min = 52.dp).clickable { onOpen(col.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CollectionBox()
                                Column {
                                    Text(col.name, style = CaddisType.strong, color = c.fg)
                                    Fine("$n ${if (n == 1) "save" else "saves"}")
                                }
                            }
                            IconBtn(CIcons.More, "Rename or delete ${col.name}", { editing = col.id; confirm.armed = null; vm.error.value = "" }, size = 20.dp)
                        }
                    }
                    HorizontalDivider(color = c.line)
                }
            }
            item {
                CCard(Modifier.fillMaxWidth().padding(top = 22.dp), padding = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CField(newName, { newName = it; vm.error.value = "" }, "Name a new collection", label = "New collection name", boxed = false, maxLength = 40, modifier = Modifier.weight(1f).padding(start = 6.dp), onIme = { vm.create(newName) { newName = "" } })
                        CButton("Create", onClick = { vm.create(newName) { newName = "" } }, style = BtnStyle.Primary, small = true)
                    }
                }
                Text(error, style = CaddisType.fine, color = c.crit, modifier = Modifier.heightIn(min = 18.dp).padding(top = 8.dp))
                Fine("Tap ⋯ on a row to rename or delete.\nDeleting a collection keeps its saves.")
            }
        }
    }
}
