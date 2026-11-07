package app.caddis.ui.item

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.caddis.ui.components.CChip
import app.caddis.ui.components.CField
import app.caddis.ui.components.CIcons
import app.caddis.ui.components.Chips
import app.caddis.ui.components.CollectionPicker
import app.caddis.ui.components.Dot
import app.caddis.ui.components.Fine
import app.caddis.ui.components.TopBar
import app.caddis.ui.intentName
import app.caddis.ui.sheets.IntentSheet
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.flow.drop

/**
 * Screen 16: one note per item, edited visually and stored as Markdown (NOTE-01). Standalone
 * notes also take an intent and collections (NOTE-02).
 */
@Composable
fun NoteScreen(onBack: () -> Unit, onManageIntents: () -> Unit, vm: ItemViewModel = hiltViewModel()) {
    val item by vm.item.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val it = item ?: return
    val c = C.colors
    val rich = rememberRichTextState()
    var loaded by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf(it.title) }
    var intentSheet by remember { mutableStateOf(false) }
    var showCollections by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(it.id) {
        if (!loaded) {
            rich.setMarkdown(it.note)
            loaded = true
        }
    }
    LaunchedEffect(rich, loaded) {
        if (loaded) snapshotFlow { rich.annotatedString }.drop(1).collect { vm.noteChanged(rich.toMarkdown()) }
    }
    DisposableEffect(Unit) { onDispose { vm.flushNote() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (it.isNote) "" else it.displayTitle, onBack = onBack) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                if (saving) Fine("Saving…") else { Dot(); Fine("Saved") }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            if (it.isNote) {
                CField(
                    title, { t -> title = t; vm.setTitle(t) }, "Untitled note", label = "Note title", boxed = false,
                    textStyle = CaddisType.h1.copy(fontSize = CaddisType.h1.fontSize * 0.86f), maxLength = 120,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            Toolbar(
                listOf(
                    ToolbarButton("B", "Bold", bold = true) { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                    ToolbarButton("I", "Italic", italic = true) { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                    ToolbarButton("•", "Bulleted list") { rich.toggleUnorderedList() },
                    ToolbarButton("1.", "Numbered list") { rich.toggleOrderedList() },
                    ToolbarButton("</>", "Code") { rich.toggleCodeSpan() },
                ),
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
                if (loaded && rich.annotatedString.text.isEmpty()) Text("Write a note", style = CaddisType.bodyLarge, color = c.fg2)
                BasicRichTextEditor(
                    state = rich,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).semantics { contentDescription = "Note" },
                    textStyle = CaddisType.bodyLarge.copy(color = c.fg),
                    cursorBrush = SolidColor(c.accent),
                )
            }
            Fine("Edited visually, stored as Markdown.", Modifier.padding(top = 14.dp))
            if (it.isNote) {
                Fine("Standalone notes also take an intent and a collection.", Modifier.padding(top = 14.dp))
                Chips(Modifier.padding(top = 8.dp)) {
                    CChip(intentName(intents, it.intentId), selected = true, onClick = { intentSheet = true }, trailing = CIcons.Down)
                    CChip(if (showCollections) "Done" else "+ Collection", onClick = { showCollections = !showCollections })
                }
                if (showCollections) CollectionPicker(collections, it.collectionIds, vm::toggleCollection, Modifier.padding(top = 10.dp))
            }
        }
    }
    if (intentSheet) IntentSheet(it, intents, onPick = vm::setIntent, onManage = { intentSheet = false; onManageIntents() }, onDismiss = { intentSheet = false })
}

private data class ToolbarButton(val text: String, val label: String, val bold: Boolean = false, val italic: Boolean = false, val onClick: () -> Unit)

@Composable
private fun Toolbar(buttons: List<ToolbarButton>) {
    val c = C.colors
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        buttons.forEach { b ->
            Box(
                Modifier
                    .widthIn(min = 44.dp)
                    .size(height = 40.dp, width = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = b.onClick)
                    .semantics { contentDescription = b.label },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    b.text,
                    style = CaddisType.strong.copy(fontWeight = FontWeight.Bold, fontStyle = if (b.italic) FontStyle.Italic else FontStyle.Normal),
                    color = c.fg, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
