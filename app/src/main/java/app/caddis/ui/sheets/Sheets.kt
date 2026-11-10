package app.caddis.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import app.caddis.domain.details.DetailEdits
import app.caddis.domain.details.EditLimits
import app.caddis.domain.model.Intent
import app.caddis.domain.model.IntentSource
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ReturnRule
import app.caddis.ui.components.BtnStyle
import app.caddis.ui.components.CButton
import app.caddis.ui.components.CField
import app.caddis.ui.components.CSheet
import app.caddis.ui.components.Fine
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Screen 9: "Bring it back" — tonight, this weekend, next week or a chosen date (BRB-03). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaterSheet(item: Item, today: LocalDate, onPick: (ReturnRule, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    CSheet(onDismiss) {
        Text("Bring it back", style = CaddisType.sheetTitle, color = C.colors.fg)
        Fine(item.displayTitle, Modifier.padding(top = 2.dp, bottom = 8.dp))
        LaterRow("Tonight", "7 pm") { onPick(ReturnRule.TONIGHT, null) }
        LaterRow("This weekend", "Saturday morning") { onPick(ReturnRule.WEEKEND, null) }
        LaterRow("Next week", "Monday") { onPick(ReturnRule.NEXT_WEEK, null) }
        LaterRow("Pick a date", "Calendar") { picking = true }
        Fine("It won’t reappear before then.", Modifier.padding(top = 12.dp))
        CButton("Cancel", onClick = onDismiss, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
    }
    if (picking) {
        val minUtc = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.plusDays(8).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    picking = false
                    if (ms != null) onPick(ReturnRule.DATE, Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                }) { Text("Set", color = C.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = C.colors.fg2) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun LaterRow(title: String, hint: String, onClick: () -> Unit) {
    val c = C.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = CaddisType.strong.copy(fontSize = CaddisType.bodyLarge.fontSize), color = c.fg, modifier = Modifier.weight(1f))
            Text(hint, style = CaddisType.body, color = c.fg2)
        }
        HorizontalDivider(color = c.line)
    }
}

/** Screen 10: change intent in one tap (INT-02). */
@Composable
fun IntentSheet(item: Item, intents: List<Intent>, onPick: (String) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val c = C.colors
    CSheet(onDismiss) {
        Text("Why are you keeping it?", style = CaddisType.sheetTitle, color = c.fg)
        Fine(
            if (item.intentSource == IntentSource.GUESSED) "Guessed: ${intents.firstOrNull { it.id == item.intentId }?.name}. Pick another to correct it." else "Set by you.",
            Modifier.padding(top = 2.dp),
        )
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            intents.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i -> IntentOption(i, i.id == item.intentId, Modifier.weight(1f)) { onPick(i.id) } }
                    if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
        Fine("The return rule follows the intent.")
        CButton("Manage intents", onClick = onManage, style = BtnStyle.Ghost, small = true)
        CButton("Done", onClick = onDismiss, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun IntentOption(i: Intent, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = C.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (on) c.accentTint else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(i.name, style = CaddisType.strong, color = c.fg)
        Fine(if (i.id == Intents.KEEP) "Stays where it is" else "Done: ${i.doneLabel}")
    }
}

/** Two-step confirm used for destructive buttons ("Delete" → "Sure? …"). */
@Composable
fun rememberConfirm(): MutableConfirm = remember { MutableConfirm() }

class MutableConfirm {
    var armed by mutableStateOf<String?>(null)
    fun tap(key: String, action: () -> Unit) {
        if (armed == key) { armed = null; action() } else armed = key
    }
    fun isArmed(key: String) = armed == key
}

@Composable
fun CenteredFine(text: String) = Fine(text, Modifier.fillMaxWidth().padding(top = 14.dp), align = TextAlign.Center)

/**
 * Edit details (MET-03): the link, title, who made it, and the description. What you type is kept
 * when details refresh; an emptied field goes back to what Caddis finds. A new link re-reads the
 * platform and fetches details again. [onSave] returns a message when the link cannot be used.
 */
@Composable
fun EditDetailsSheet(item: Item, onSave: suspend (String?, DetailEdits) -> String?, onDone: () -> Unit) {
    var url by rememberSaveable(item.id) { mutableStateOf(item.url.orEmpty()) }
    var title by rememberSaveable(item.id) { mutableStateOf(item.title) }
    var creator by rememberSaveable(item.id) { mutableStateOf(item.creator) }
    var description by rememberSaveable(item.id) { mutableStateOf(item.description) }
    var error by remember(item.id) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val linkChanged = !item.isNote && url.trim() != item.url
    val save = {
        if (!saving) {
            saving = true
            scope.launch {
                val message = onSave(if (item.isNote) null else url, DetailEdits(title, description, creator))
                saving = false
                if (message == null) onDone() else error = message
            }
        }
    }
    CSheet(onDone) {
        Text("Edit details", style = CaddisType.sheetTitle, color = C.colors.fg)
        Fine(
            "Your words are kept when details refresh. Leave a field empty to let Caddis fill it in.",
            Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        if (!item.isNote) {
            Fine("Link", Modifier.padding(bottom = 6.dp))
            CField(
                url, { url = it; error = "" }, "https://", label = "Link", maxLength = 2000, lines = 3,
                keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth(),
            )
            when {
                error.isNotEmpty() -> Text(error, style = CaddisType.fine.copy(fontSize = CaddisType.sub.fontSize), color = C.colors.crit, modifier = Modifier.padding(top = 6.dp))
                linkChanged -> Fine("Caddis will read the new link and fetch its details again.", Modifier.padding(top = 6.dp))
            }
        }
        Fine("Title", Modifier.padding(top = 12.dp, bottom = 6.dp))
        CField(title, { title = it }, item.displayTitle, label = "Title", maxLength = EditLimits.TITLE, lines = 3, modifier = Modifier.fillMaxWidth())
        Fine("By", Modifier.padding(top = 12.dp, bottom = 6.dp))
        CField(creator, { creator = it }, "Who made it", label = "By", maxLength = EditLimits.CREATOR, modifier = Modifier.fillMaxWidth())
        Fine("Description", Modifier.padding(top = 12.dp, bottom = 6.dp))
        CField(description, { description = it }, "What it’s about (optional)", label = "Description", maxLength = EditLimits.DESCRIPTION, lines = 5, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CButton("Cancel", onClick = onDone, modifier = Modifier.weight(1f))
            CButton(if (saving) "Saving…" else "Save", onClick = { save() }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
        }
    }
}
