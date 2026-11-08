package app.caddis.ui.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.caddis.ui.LocalOpenedItem
import app.caddis.ui.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.caddis.domain.details.formatLength
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.notes.Markdown
import app.caddis.domain.schedule.doneLabel
import app.caddis.ui.components.BtnStyle
import app.caddis.ui.components.CButton
import app.caddis.ui.components.CCard
import app.caddis.ui.components.CChip
import app.caddis.ui.components.CField
import app.caddis.ui.components.CIcons
import app.caddis.ui.components.Chips
import app.caddis.ui.components.CollectionPicker
import app.caddis.ui.components.Eyebrow
import app.caddis.ui.components.Fine
import app.caddis.ui.components.IconBtn
import app.caddis.ui.components.Sub
import app.caddis.ui.components.Thumb
import app.caddis.ui.intentName
import app.caddis.ui.savedLabel
import app.caddis.ui.sheets.EditDetailsSheet
import app.caddis.ui.sheets.IntentSheet
import app.caddis.ui.sheets.LaterSheet
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType

/** Screen 15: why you kept it, where it came from, your note; Open, Done, Delete (LIB-04). */
@Composable
fun ItemScreen(onBack: () -> Unit, onNote: (String) -> Unit, onManageIntents: () -> Unit, vm: ItemViewModel = hiltViewModel()) {
    val item by vm.item.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<String?>(null) }
    var editReason by rememberSaveable { mutableStateOf(false) }
    var showCollections by rememberSaveable { mutableStateOf(false) }
    val c = C.colors
    // The list we came from handed over the item, so the screen (and its thumbnail) exist on the first frame.
    val opened = LocalOpenedItem.current
    val origin = remember { opened.origin }
    val preview = remember { opened.previewFor(vm.id) }
    val it = item ?: preview ?: return

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(CIcons.Back, "Back", onBack)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            IconBtn(CIcons.Edit, "Edit details", { sheet = "edit" })
            IconBtn(CIcons.More, "Bring back later", { sheet = "later" })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
            Thumb(it, size = 1000.dp, height = 150.dp, radius = 16.dp, modifier = Modifier.fillMaxWidth(), share = origin.ifBlank { null })
            Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CChip(intentName(intents, it.intentId), selected = true, onClick = { sheet = "intent" }, trailing = CIcons.Down, contentDescription = "Intent: ${intentName(intents, it.intentId)}. Change")
                CChip(it.platform.label)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Fine(it.kind.label + formatLength(it.lengthSeconds, it.kind.isVideo).let { l -> if (l.isBlank()) "" else " · $l" })
            }
            Text(it.displayTitle, style = CaddisType.itemTitle, color = c.fg)
            val by = buildString {
                if (it.creator.isNotBlank()) append("by ").append(it.creator)
                if (it.sourceFrom.isNotBlank()) append(if (isEmpty()) "" else " · ").append("from ").append(it.sourceFrom)
                if (it.sourceApp.isNotBlank()) append(if (isEmpty()) "" else " ").append("via ").append(it.sourceApp)
            }
            if (by.isNotBlank()) Sub(by, Modifier.padding(top = 4.dp))
            if (it.description.isNotBlank()) Sub(it.description, Modifier.padding(top = 8.dp), style = CaddisType.body)

            Eyebrow("Why you kept it", Modifier.padding(top = 18.dp, bottom = 8.dp))
            if (editReason) {
                var draft by remember { mutableStateOf(it.reason) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CField(draft, { d -> draft = d }, "A thought for future you", label = "Reason", maxLength = 140, modifier = Modifier.weight(1f))
                    CButton("Save", onClick = { vm.setReason(draft); editReason = false }, style = BtnStyle.Primary, small = true)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (it.reason.isNotBlank()) Text("“${it.reason}”", style = CaddisType.bodyLarge, color = c.fg, modifier = Modifier.weight(1f))
                    else Fine("No reason added", Modifier.weight(1f))
                    CButton("Edit", onClick = { editReason = true }, style = BtnStyle.Ghost, small = true)
                }
            }

            CCard(Modifier.fillMaxWidth().padding(top = 14.dp), onClick = { onNote(it.id) }) {
                Fine("Note")
                val preview = Markdown.preview(it.note)
                Text(preview.ifBlank { "Add a note" }, style = CaddisType.body, color = if (preview.isBlank()) c.fg2 else c.fg, modifier = Modifier.padding(top = 4.dp))
            }

            Chips(Modifier.padding(top = 14.dp)) {
                collections.filter { col -> col.id in it.collectionIds }.forEach { col -> CChip(col.name) }
                CChip(if (showCollections) "Done" else "+ Add", onClick = { showCollections = !showCollections })
            }
            AnimatedVisibility(showCollections, enter = fadeIn(tween(Motion.Short)) + expandVertically(), exit = fadeOut(tween(Motion.Quick)) + shrinkVertically()) {
                CollectionPicker(collections, it.collectionIds, vm::toggleCollection, Modifier.padding(top = 10.dp))
            }

            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Fine(it.savedLabel() + if (it.detailsFetchedAt != null) " · details loaded" else "", Modifier.weight(1f))
                if (it.url != null) CButton("Refresh", onClick = { vm.refresh() }, style = BtnStyle.Ghost, small = true)
            }
        }
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (it.url != null) CButton("Open", onClick = { vm.open() }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            if (it.status == ItemStatus.DONE) CButton("Mark active", onClick = { vm.markActive() }, modifier = Modifier.weight(1.3f))
            else CButton(doneLabel(intents, it.intentId), onClick = { vm.done() }, modifier = Modifier.weight(1.3f))
            CButton("Delete", onClick = { vm.delete(); onBack() }, modifier = Modifier.weight(1f))
        }
    }

    when (sheet) {
        "later" -> LaterSheet(it, vm.today(), onPick = { r, d -> vm.later(r, d); sheet = null }, onDismiss = { sheet = null })
        "edit" -> EditDetailsSheet(it, onSave = vm::saveDetails, onDone = { sheet = null })
        "intent" -> IntentSheet(it, intents, onPick = vm::setIntent, onManage = { sheet = null; onManageIntents() }, onDismiss = { sheet = null })
    }
}
