package com.nexxlabs.caddis.ui.sheets

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import com.nexxlabs.caddis.domain.details.DetailEdits
import com.nexxlabs.caddis.domain.details.EditLimits
import com.nexxlabs.caddis.domain.model.EditableField
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.IntentSource
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.Platform
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CField
import com.nexxlabs.caddis.ui.components.CSheet
import com.nexxlabs.caddis.ui.components.DetailField
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.canReset
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType

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
        Fine(if (i.id == Intents.KEEP) "For when you need it" else "Done: ${i.doneLabel}")
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
    var platform by rememberSaveable(item.id) { mutableStateOf(item.platform.label) }
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
                val message = onSave(if (item.isNote) null else url, DetailEdits(title, description, creator, platform.takeIf { !item.isNote }))
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
        if (!item.isNote) {
            DetailField(
                "Platform", platform, { platform = it }, Platform.fromUrl(url.ifBlank { item.url.orEmpty() }).label, Platform.MAX_SITE_NAME,
                onReset = if (canReset(platform, item.platform.label, EditableField.PLATFORM in item.userEdited)) ({ platform = "" }) else null,
            )
        }
        DetailField(
            "Title", title, { title = it }, item.displayTitle, EditLimits.TITLE, lines = 3,
            onReset = if (canReset(title, item.title, EditableField.TITLE in item.userEdited)) ({ title = "" }) else null,
        )
        DetailField(
            "By", creator, { creator = it }, "Who made it", EditLimits.CREATOR,
            onReset = if (canReset(creator, item.creator, EditableField.CREATOR in item.userEdited)) ({ creator = "" }) else null,
        )
        DetailField(
            "Description", description, { description = it }, "What it’s about (optional)", EditLimits.DESCRIPTION, lines = 5,
            onReset = if (canReset(description, item.description, EditableField.DESCRIPTION in item.userEdited)) ({ description = "" }) else null,
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CButton("Cancel", onClick = onDone, modifier = Modifier.weight(1f))
            CButton(if (saving) "Saving…" else "Save", onClick = { save() }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
        }
    }
}
