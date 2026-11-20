package com.nexxlabs.caddis.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nexxlabs.caddis.domain.model.IntentRule
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.TimeOfDay
import com.nexxlabs.caddis.domain.model.label
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CCard
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.CField
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.Chevron
import com.nexxlabs.caddis.ui.components.Chips
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.IconBtn
import com.nexxlabs.caddis.ui.components.TopBar
import com.nexxlabs.caddis.ui.sheets.rememberConfirm
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import java.time.DayOfWeek

/** Return rules per intent (INT-04): one card of rows; the open one shows its editor underneath. */
@Composable
fun RulesScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(Intents.COOK) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Return rules", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            Fine("Tap an intent to change when its saves come back.", Modifier.padding(start = 2.dp, bottom = 8.dp))
            SettingsGroup {
                intents.forEachIndexed { i, row ->
                    val r = rules[row.intent.id]
                    if (i > 0) GroupDivider(12.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(enabled = r != null) { open = if (open == row.intent.id) null else row.intent.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(row.intent.name, style = CaddisType.strong, color = C.colors.fg, modifier = Modifier.weight(1f))
                        Fine(r.label())
                        if (r != null) {
                            if (open == row.intent.id) Icon(CIcons.Up, null, tint = C.colors.fg2, modifier = Modifier.size(18.dp)) else Chevron()
                        }
                    }
                    if (r != null && open == row.intent.id) RuleEditor(r, vm::updateRule)
                }
            }
        }
    }
}

@Composable
private fun RuleEditor(r: IntentRule, onChange: (IntentRule) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(Modifier.fillMaxWidth().background(C.colors.tint).padding(14.dp)) {
        Fine("Days")
        Row(Modifier.padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEachIndexed { i, d ->
                val on = d in r.days
                CChip(letters[i], on, onClick = { onChange(r.copy(days = if (on) r.days - d else r.days + d)) }, modifier = Modifier.widthIn(min = 38.dp), contentDescription = d.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
        Fine("Time of day")
        Chips(Modifier.padding(top = 6.dp, bottom = 12.dp)) {
            TimeOfDay.entries.forEach { t -> CChip(t.label, r.timeOfDay == t, onClick = { onChange(r.copy(timeOfDay = t)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Max per day", style = CaddisType.body, color = C.colors.fg, modifier = Modifier.weight(1f))
            CChip("−", onClick = { if (r.maxPerDay > 1) onChange(r.copy(maxPerDay = r.maxPerDay - 1)) }, contentDescription = "Fewer")
            Text(r.maxPerDay.toString(), style = CaddisType.strong, color = C.colors.fg, modifier = Modifier.padding(horizontal = 10.dp))
            CChip("+", onClick = { if (r.maxPerDay < 3) onChange(r.copy(maxPerDay = r.maxPerDay + 1)) }, contentDescription = "More")
        }
    }
}

/** Create, rename and delete intents; set the Done label (INT-05). */
@Composable
fun IntentsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val rows by vm.intents.collectAsStateWithLifecycle()
    val err by vm.intentError.collectAsStateWithLifecycle()
    val newErr by vm.newIntentError.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newVerb by rememberSaveable { mutableStateOf("") }
    val confirm = rememberConfirm()
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Intents", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            Fine("Intents say why you kept something. They set the Done button and when a save comes back.", Modifier.padding(bottom = 6.dp))
            rows.forEach { (i, n) ->
                val locked = i.isProtected
                if (editing == i.id && !locked) {
                    var name by remember(i.id) { mutableStateOf(i.name) }
                    var verb by remember(i.id) { mutableStateOf(i.doneLabel) }
                    CCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Fine("Name")
                        CField(name, { name = it }, "Name", label = "Intent name", maxLength = 20, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Fine("Done button says", Modifier.padding(top = 10.dp))
                        CField(verb, { verb = it }, "Done", label = "Done button label", maxLength = 16, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CButton("Save", onClick = { vm.updateIntent(i.id, name, verb) { editing = null } }, style = BtnStyle.Primary, small = true)
                            CButton(
                                if (confirm.isArmed(i.id)) "Sure? ${if (n == 1) "1 save moves" else "$n saves move"} to Just keep" else "Delete",
                                onClick = { confirm.tap(i.id) { vm.deleteIntent(i.id) { editing = null } } },
                                style = BtnStyle.Danger, small = true,
                            )
                        }
                        Text(err, style = CaddisType.fine, color = c.crit, modifier = Modifier.heightIn(min = 18.dp).padding(top = 6.dp))
                    }
                } else {
                    Column {
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (!locked) Modifier.clickable { editing = i.id; confirm.armed = null; vm.intentError.value = "" } else Modifier), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(i.name, style = CaddisType.strong, color = c.fg)
                                Fine("$n ${if (n == 1) "save" else "saves"} · Done says “${i.doneLabel}”" + if (locked) " · Default, always kept" else "")
                            }
                            if (!locked) IconBtn(CIcons.More, "Edit or delete ${i.name}", { editing = i.id; confirm.armed = null }, size = 20.dp)
                        }
                        HorizontalDivider(color = c.line)
                    }
                }
            }
            CCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text("New intent", style = CaddisType.strong, color = c.fg)
                CField(newName, { newName = it; vm.newIntentError.value = "" }, "Name, for example Gift", label = "New intent name", maxLength = 20, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                CField(newVerb, { newVerb = it }, "Done button, for example Gifted (optional)", label = "Done button label", maxLength = 16, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                CButton("Create intent", onClick = { vm.createIntent(newName, newVerb) { newName = ""; newVerb = "" } }, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(newErr, style = CaddisType.fine, color = c.crit, modifier = Modifier.heightIn(min = 18.dp).padding(top = 6.dp))
            }
            Fine("New intents return on weekday afternoons until you change their return rule. Deleting an intent moves its saves to Just keep.", Modifier.padding(top = 8.dp))
        }
    }
}
