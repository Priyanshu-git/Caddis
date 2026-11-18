package com.nexxlabs.caddis.ui.item

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.nexxlabs.caddis.domain.link.LinkCheck
import com.nexxlabs.caddis.domain.link.Links
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.notes.EditHistory
import com.nexxlabs.caddis.domain.notes.NoteMarkdown
import com.nexxlabs.caddis.domain.schedule.doneLabel
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.CField
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.CSheet
import com.nexxlabs.caddis.ui.components.Chips
import com.nexxlabs.caddis.ui.components.CollectionChips
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.IconBtn
import com.nexxlabs.caddis.ui.components.TopBar
import com.nexxlabs.caddis.ui.intentName
import com.nexxlabs.caddis.ui.sheets.IntentSheet
import com.nexxlabs.caddis.ui.sheets.LaterSheet
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import kotlinx.coroutines.flow.drop

/** What undo goes back to: the note as stored, and where the caret was. */
private data class NoteState(val markdown: String, val caret: Int)

private enum class NoteSheet { Intent, Collections, Later, Link }

/**
 * Screen 16: a standalone note, edited visually and stored as Markdown (NOTE-01), with its intent and
 * collections under the title (NOTE-02). The formatting bar sits above the keyboard and shows only while
 * the body is being written; the ⋮ menu holds done, later and delete.
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
    var sheet by remember { mutableStateOf<NoteSheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    // The save status shows only once something was changed here: an untouched note has nothing to report.
    var edited by rememberSaveable { mutableStateOf(false) }
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    var backToBody by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }
    val history = remember { EditHistory(NoteState("", 0), same = { a, b -> a.markdown == b.markdown }) }
    var restoring by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }

    LaunchedEffect(c.accent) { rich.config.linkColor = c.accent }
    LaunchedEffect(it.id) {
        if (!loaded) {
            rich.setHtml(NoteMarkdown.toHtml(vm.currentNote()))
            history.replace(NoteState(NoteMarkdown.fromHtml(rich.toHtml()), rich.selection.max))
            loaded = true
        }
    }
    LaunchedEffect(rich, loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { rich.annotatedString }.drop(1).collect {
            val md = NoteMarkdown.fromHtml(rich.toHtml())
            val now = NoteState(md, rich.selection.max)
            if (restoring) {
                restoring = false
                history.replace(now)
                edited = true
            } else if (history.record(now, SystemClock.uptimeMillis())) edited = true
            canUndo = history.canUndo
            vm.noteChanged(md)
        }
    }
    // A new (empty) note opens ready to write in.
    LaunchedEffect(loaded) {
        if (loaded && !autoFocused && rich.annotatedString.text.isEmpty()) {
            autoFocused = true
            bodyFocus.requestFocus()
        }
    }
    // Closing the link sheet puts the caret back where it was, keyboard and all.
    LaunchedEffect(sheet) {
        if (sheet == null && backToBody) {
            backToBody = false
            bodyFocus.requestFocus()
        }
    }
    DisposableEffect(Unit) { onDispose { vm.flushNote() } }

    val undo = {
        history.undo()?.let { s ->
            restoring = true
            rich.setHtml(NoteMarkdown.toHtml(s.markdown))
            rich.selection = TextRange(s.caret.coerceIn(0, rich.annotatedString.text.length))
            canUndo = history.canUndo
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (it.isNote) "" else it.displayTitle, onBack = onBack) {
            if (edited) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 2.dp)) {
                    if (saving) Fine("Saving…") else {
                        Icon(CIcons.Check, null, tint = c.fg2, modifier = Modifier.size(14.dp))
                        Fine("Saved")
                    }
                }
            }
            Box {
                IconBtn(CIcons.More, "More", { menu = true })
                NoteMenu(
                    open = menu,
                    doneLabel = if (it.status == ItemStatus.DONE) "Mark active" else doneLabel(intents, it.intentId),
                    onDismiss = { menu = false },
                    onDone = { menu = false; if (it.status == ItemStatus.DONE) vm.markActive() else vm.done() },
                    onLater = { menu = false; sheet = NoteSheet.Later },
                    onDelete = { menu = false; vm.delete(); onBack() },
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewport = maxHeight
        var headerPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { s -> headerPx = s.height }) {
            if (it.isNote) {
                CField(
                    title, { t -> title = t; edited = true; vm.setTitle(t) }, "Untitled note", label = "Note title", boxed = false,
                    textStyle = CaddisType.h1.copy(fontSize = CaddisType.h1.fontSize * 0.86f), maxLength = 120,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                Chips(Modifier.padding(top = 6.dp)) {
                    val intent = intentName(intents, it.intentId)
                    CChip(intent, selected = true, onClick = { sheet = NoteSheet.Intent }, trailing = CIcons.Down, contentDescription = "Reason: $intent")
                    val mine = collections.filter { col -> col.id in it.collectionIds }
                    mine.forEach { col ->
                        CChip(col.name, selected = true, onClick = { sheet = NoteSheet.Collections }, contentDescription = "Collection: ${col.name}")
                    }
                    CChip(
                        if (mine.isEmpty()) "Collection" else "Add", onClick = { sheet = NoteSheet.Collections },
                        leading = CIcons.Plus, contentDescription = "Add to a collection",
                    )
                }
                HorizontalDivider(color = c.line, modifier = Modifier.padding(top = 16.dp))
            }
            }
            // The body reaches down to the bottom of the screen, so a tap anywhere under the text writes in it.
            val bodyMin = (viewport - with(density) { headerPx.toDp() } - 14.dp - 28.dp).coerceAtLeast(160.dp)
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 28.dp)) {
                if (loaded && rich.annotatedString.text.isEmpty()) Text("Write a note", style = CaddisType.bodyLarge, color = c.fg2)
                BasicRichTextEditor(
                    state = rich,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = bodyMin)
                        .focusRequester(bodyFocus)
                        .onFocusChanged { f -> bodyFocused = f.isFocused }
                        .semantics { contentDescription = "Note" },
                    textStyle = CaddisType.bodyLarge.copy(color = c.fg),
                    cursorBrush = SolidColor(c.accent),
                )
            }
        }
        }
        if (bodyFocused) {
            // Each button lights up when its style applies where the caret is (or to the selection),
            // including a style just switched on that has no text yet.
            val style = rich.currentSpanStyle
            FormatBar(
                listOf(
                    Tool(CIcons.Bold, "Bold", active = style.fontWeight == FontWeight.Bold) { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                    Tool(CIcons.Italic, "Italic", active = style.fontStyle == FontStyle.Italic) { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                    Tool(CIcons.BulletList, "Bulleted list", active = rich.isUnorderedList) { rich.toggleUnorderedList() },
                    Tool(CIcons.NumberList, "Numbered list", active = rich.isOrderedList) { rich.toggleOrderedList() },
                    Tool(CIcons.Code, "Code", active = rich.isCodeSpan) { rich.toggleCodeSpan() },
                    Tool(CIcons.Link, "Link", active = rich.isLink) { backToBody = true; sheet = NoteSheet.Link },
                ),
                undo = Tool(CIcons.Undo, "Undo", enabled = canUndo) { undo() },
            )
        }
    }

    when (sheet) {
        NoteSheet.Intent -> IntentSheet(it, intents, onPick = vm::setIntent, onManage = { sheet = null; onManageIntents() }, onDismiss = { sheet = null })
        NoteSheet.Collections -> CollectionsSheet(
            all = collections, selected = it.collectionIds, onToggle = vm::toggleCollection,
            onCreate = vm::createCollection, onDismiss = { sheet = null },
        )
        NoteSheet.Later -> LaterSheet(it, vm.today(), onPick = { r, d -> vm.later(r, d); sheet = null }, onDismiss = { sheet = null })
        NoteSheet.Link -> LinkSheet(rich, onDismiss = { sheet = null })
        null -> Unit
    }
}

@Composable
private fun NoteMenu(open: Boolean, doneLabel: String, onDismiss: () -> Unit, onDone: () -> Unit, onLater: () -> Unit, onDelete: () -> Unit) {
    val c = C.colors
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, containerColor = c.surface, shape = RoundedCornerShape(14.dp)) {
        DropdownMenuItem(text = { Text(doneLabel, style = CaddisType.body, color = c.fg) }, onClick = onDone)
        DropdownMenuItem(text = { Text("Bring back later", style = CaddisType.body, color = c.fg) }, onClick = onLater)
        DropdownMenuItem(text = { Text("Delete", style = CaddisType.body, color = c.crit) }, onClick = onDelete)
    }
}

private class Tool(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** The formatting bar: flat, full width, right above the keyboard. Undo sits apart on the right. */
@Composable
private fun FormatBar(tools: List<Tool>, undo: Tool) {
    val c = C.colors
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            tools.forEach { ToolButton(it, Modifier.weight(1f)) }
            Box(Modifier.width(1.dp).height(22.dp).background(c.line))
            ToolButton(undo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolButton(t: Tool, modifier: Modifier) {
    val c = C.colors
    Box(modifier.height(48.dp).clickable(role = Role.Button, onClick = t.onClick).semantics {
        contentDescription = t.label
        selected = t.active
    }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 40.dp, height = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (t.active) c.accentTint else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                t.icon, null, tint = if (t.active) c.accent else c.fg2,
                modifier = Modifier.size(20.dp).alpha(if (t.enabled) 1f else 0.4f),
            )
        }
    }
}

/** Every collection as a tick chip, with "New" first; closing with a name still typed creates it. */
@Composable
private fun CollectionsSheet(
    all: List<com.nexxlabs.caddis.domain.model.Collection>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val close = {
        draft?.trim()?.takeIf { it.isNotEmpty() }?.let(onCreate)
        draft = null
        onDismiss()
    }
    CSheet(close) {
        Text("Collections", style = CaddisType.sheetTitle, color = C.colors.fg)
        Fine("A note can be in as many as you like.", Modifier.padding(top = 2.dp, bottom = 12.dp))
        CollectionChips(all = all, selected = selected, onToggle = onToggle, onCreate = onCreate, draft = draft, onDraft = { draft = it })
        CButton("Done", onClick = close, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/**
 * Adds a link where the caret is, turns the selected words into one, or changes or removes the link the
 * caret is in. Only web links are taken (CAP-01).
 */
@Composable
private fun LinkSheet(rich: RichTextState, onDismiss: () -> Unit) {
    val c = C.colors
    val existing = remember { if (rich.isLink) rich.selectedLinkUrl else null }
    val needsText = remember { existing == null && rich.selection.collapsed }
    var text by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf(existing.orEmpty()) }
    var error by remember { mutableStateOf("") }
    val save = {
        when (val check = Links.check(address)) {
            is LinkCheck.Invalid -> error = check.message
            is LinkCheck.Valid -> {
                when {
                    existing != null -> rich.updateLink(check.url)
                    needsText -> rich.addLink(text.trim().ifEmpty { address.trim() }, check.url)
                    else -> rich.addLinkToSelection(check.url)
                }
                onDismiss()
            }
        }
    }
    CSheet(onDismiss) {
        Text(if (existing != null) "Edit link" else "Add a link", style = CaddisType.sheetTitle, color = c.fg)
        if (needsText) {
            Fine("Text", Modifier.padding(top = 12.dp, bottom = 6.dp))
            CField(text, { text = it }, "What to show (optional)", label = "Link text", maxLength = 200, modifier = Modifier.fillMaxWidth())
        }
        Fine("Address", Modifier.padding(top = 12.dp, bottom = 6.dp))
        CField(
            address, { address = it; error = "" }, "https://", label = "Link address", maxLength = 2000,
            keyboardType = KeyboardType.Uri, onIme = { save() }, modifier = Modifier.fillMaxWidth(),
        )
        if (error.isNotEmpty()) Text(error, style = CaddisType.fine.copy(fontSize = CaddisType.sub.fontSize), color = c.crit, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing != null) CButton("Remove link", onClick = { rich.removeLink(); onDismiss() }, style = BtnStyle.Danger, modifier = Modifier.weight(1f))
            else CButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            CButton("Save", onClick = { save() }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
        }
    }
}
