package app.caddis.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caddis.domain.model.Collection
import app.caddis.domain.model.Intent
import app.caddis.domain.model.IntentSource
import app.caddis.domain.model.Item
import app.caddis.domain.model.Platform
import app.caddis.ui.components.BtnStyle
import app.caddis.ui.components.CButton
import app.caddis.ui.components.CCard
import app.caddis.ui.components.CChip
import app.caddis.ui.components.CField
import app.caddis.ui.components.CIcons
import app.caddis.ui.components.CSheet
import app.caddis.ui.components.Chips
import app.caddis.ui.components.CollectionPicker
import app.caddis.ui.components.Eyebrow
import app.caddis.ui.components.Fine
import app.caddis.ui.components.Sub
import app.caddis.ui.components.Thumb
import app.caddis.ui.dayMonth
import app.caddis.ui.intentName
import app.caddis.ui.meta
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType
import java.time.ZoneId

/** Renders whichever capture sheet is open. Used by MainActivity and ShareActivity. */
@Composable
fun CaptureSheetHost(vm: CaptureViewModel) {
    val sheet by vm.sheet.collectAsState()
    val intents by vm.intents.collectAsState()
    val collections by vm.collections.collectAsState()
    when (val s = sheet) {
        CaptureSheet.None -> Unit
        is CaptureSheet.Add -> AddSheet(s, vm)
        is CaptureSheet.Saved -> {
            val item by vm.savedItem.collectAsState()
            item?.let { SaveSheet(it, s.sharedFrom, intents, collections, vm) }
        }
        is CaptureSheet.Duplicate -> DuplicateSheet(s, intents, vm)
    }
}

/** Screen 5: Add link or note (CAP-04/05). */
@Composable
private fun AddSheet(s: CaptureSheet.Add, vm: CaptureViewModel) {
    val c = C.colors
    var noteTitle by rememberSaveable { mutableStateOf("") }
    CSheet(onDismiss = { vm.dismiss() }) {
        Segmented(
            options = listOf("Link", "Note"),
            selected = if (s.mode == CaptureSheet.Add.Mode.LINK) 0 else 1,
            onSelect = { vm.setAddMode(if (it == 0) CaptureSheet.Add.Mode.LINK else CaptureSheet.Add.Mode.NOTE) },
            label = "Add",
        )
        if (s.mode == CaptureSheet.Add.Mode.LINK) {
            CField(
                value = s.url, onValueChange = vm::setAddUrl, placeholder = "Paste or type a link", label = "Link",
                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, onIme = { vm.submitLink(s.url) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.error.isNotEmpty()) Text(s.error, style = CaddisType.fine.copy(fontSize = CaddisType.sub.fontSize), color = c.crit, modifier = Modifier.padding(top = 8.dp))
            if (s.url.isEmpty() && s.clipboard != null) {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.accentTint)
                        .border(1.5.dp, c.accent, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Fine("From your clipboard")
                        Text(s.clipboard.removePrefix("https://"), style = CaddisType.strong, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CButton("Use", onClick = { vm.setAddUrl(s.clipboard) }, style = BtnStyle.Primary, small = true)
                }
                Fine("Read from the clipboard only after you tapped Add.", Modifier.padding(top = 10.dp))
            }
            CButton("Continue", onClick = { vm.submitLink(s.url) }, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        } else {
            CField(value = noteTitle, onValueChange = { noteTitle = it }, placeholder = "Note title (optional)", label = "Note title", modifier = Modifier.fillMaxWidth(), maxLength = 120)
            CButton("Continue", onClick = { vm.submitNote(noteTitle) }, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

/** Screen 3: "Saved to Caddis" — confirms and asks one question (CAP-02, INT-02/03). */
@Composable
private fun SaveSheet(item: Item, sharedFrom: String, intents: List<Intent>, collections: List<Collection>, vm: CaptureViewModel) {
    val c = C.colors
    var reason by remember(item.id) { mutableStateOf(item.reason) }
    var from by remember(item.id) { mutableStateOf(item.sourceFrom) }
    var showCollections by rememberSaveable { mutableStateOf(false) }
    var showFrom by rememberSaveable { mutableStateOf(false) }
    val loading = !item.isNote && item.detailsFetchedAt == null && item.title.isBlank() && !item.platform.isMeta
    val close = {
        if (from != item.sourceFrom) vm.setFrom(item.id, from)
        vm.dismiss(item.id to reason)
    }
    CSheet(onDismiss = close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(CIcons.Check, null, tint = c.good, modifier = Modifier.size(22.dp))
            Text("Saved to Caddis", style = CaddisType.sheetTitle, color = c.fg, modifier = Modifier.weight(1f))
            if (sharedFrom.isNotBlank()) Fine("Shared from $sharedFrom")
        }
        CCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(item, 64.dp, loading = loading)
                Column(Modifier.weight(1f)) {
                    if (loading) {
                        SkeletonLine(0.9f); Spacer(Modifier.height(8.dp)); SkeletonLine(0.62f); Spacer(Modifier.height(8.dp)); SkeletonLine(0.36f)
                        Fine("Loading details", Modifier.padding(top = 8.dp))
                    } else {
                        Text(item.displayTitle, style = CaddisType.strong, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Fine(item.meta())
                        if (item.creator.isNotBlank()) Fine(item.creator)
                    }
                }
            }
        }
        Eyebrow("Why are you keeping it?", Modifier.padding(top = 16.dp, bottom = 8.dp))
        Chips {
            intents.forEach { i -> CChip(i.name, selected = item.intentId == i.id, onClick = { vm.setIntent(item.id, i.id) }) }
        }
        val subject = guessSubject(item)
        Fine(
            if (item.intentSource == IntentSource.GUESSED) "Guessed from ${article(subject)} $subject. Tap to change." else "Set by you.",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        CField(
            value = reason,
            onValueChange = { reason = it; vm.setReason(item.id, it) },
            placeholder = "A thought for future you (optional)",
            label = "A thought for future you",
            maxLength = 140,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showFrom) {
            CField(value = from, onValueChange = { from = it }, placeholder = "Who sent it? e.g. Rohan", label = "From", maxLength = 60, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (showCollections) {
            CollectionPicker(collections, item.collectionIds, { id, on -> vm.toggleCollection(item.id, id, on) }, Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CButton(if (showCollections) "Hide collections" else "+ Add to collection", onClick = { showCollections = !showCollections }, style = BtnStyle.Ghost, small = true)
                if (!showFrom) CButton("+ Who sent it", onClick = { showFrom = true }, style = BtnStyle.Ghost, small = true)
            }
            CButton("Done", onClick = close, style = BtnStyle.Primary)
        }
        Fine("Tap outside to close. The save stays.", Modifier.fillMaxWidth().padding(top = 6.dp), align = TextAlign.Center)
    }
}

/** Screen 4: "Already in your library" (CAP-06). */
@Composable
private fun DuplicateSheet(s: CaptureSheet.Duplicate, intents: List<Intent>, vm: CaptureViewModel) {
    val c = C.colors
    val saved = s.existing.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().dayMonth()
    CSheet(onDismiss = { vm.dismiss() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(CIcons.Alert, null, tint = c.warn, modifier = Modifier.size(22.dp))
            Text("Already in your library", style = CaddisType.sheetTitle, color = c.fg)
        }
        Sub("Same link, saved on $saved.", Modifier.padding(bottom = 12.dp))
        CCard(onClick = vm::openEarlier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(s.existing, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(s.existing.displayTitle, style = CaddisType.strong, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Fine("${intentName(intents, s.existing.intentId)} · saved $saved")
                }
            }
        }
        CButton("Open earlier save", onClick = vm::openEarlier, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        CButton("Save again", onClick = { vm.saveAgain() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Fine("Saving again keeps both copies.", Modifier.fillMaxWidth().padding(top = 10.dp), align = TextAlign.Center)
    }
}

@Composable
fun SkeletonLine(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.colors.line).alpha(0.8f))
}

/** Two-option pill switch (prototype `.seg2`). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = C.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .border(1.dp, c.line, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) c.accentTint else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (on) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(999.dp)) else Modifier)
                    .clickableRole { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = CaddisType.button, color = if (on) c.fg else c.fg2)
            }
        }
    }
}

/** What the intent guess was based on: "Instagram Reel", "X Post", or just "webpage" for the open web. */
private fun guessSubject(item: Item): String =
    if (item.platform == Platform.WEB) item.kind.label.let { if (it == "Webpage") "webpage" else it }
    else "${item.platform.label} ${item.kind.label}"

/** "an" before a vowel sound: Instagram, Article, and the letter X ("ex"). */
private fun article(word: String) = if (word.firstOrNull()?.uppercaseChar() in setOf('A', 'E', 'I', 'O', 'U', 'X')) "an" else "a"

private fun Modifier.clickableRole(onClick: () -> Unit): Modifier =
    this.clickable(role = Role.Tab, onClick = onClick)
