package com.nexxlabs.caddis.ui.profile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexxlabs.caddis.CaddisApp
import com.nexxlabs.caddis.domain.model.IntentRule
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.TimeOfDay
import com.nexxlabs.caddis.domain.model.label
import com.nexxlabs.caddis.ui.components.AppMark
import com.nexxlabs.caddis.ui.components.BigTitle
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CCard
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.CField
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.Chevron
import com.nexxlabs.caddis.ui.components.Chips
import com.nexxlabs.caddis.ui.components.Eyebrow
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.IconBtn
import com.nexxlabs.caddis.ui.components.SettingRow
import com.nexxlabs.caddis.ui.components.Sub
import com.nexxlabs.caddis.ui.components.Toggle
import com.nexxlabs.caddis.ui.components.TopBar
import com.nexxlabs.caddis.ui.sheets.rememberConfirm
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import com.nexxlabs.caddis.update.UpdateState
import java.time.DayOfWeek

/** Asks for the notification permission (Android 13+) only when the person turns a reminder on. */
@Composable
internal fun rememberNotificationToggle(onResult: (Boolean) -> Unit): (Boolean) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onResult(granted) }
    return { on ->
        if (on && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(on)
    }
}

/** A settings row without the divider, for rows inside a card. */
@Composable
internal fun SettingRowNoLine(title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CaddisType.strong, color = C.colors.fg)
            Fine(sub)
        }
        trailing()
    }
}

/** Screen 23: Profile — preferences, background work, your data, privacy and account (ACC-04). */
@Composable
fun ProfileScreen(navigate: (String) -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    val st = s ?: return
    val update by vm.update.collectAsStateWithLifecycle()
    val updateDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    Column(Modifier.fillMaxSize()) {
        BigTitle("Profile")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 100.dp)) {
            when (update) {
                is UpdateState.Available -> {
                    Eyebrow("Update")
                    SettingRow("Update Caddis", "A newer version is on Google Play.", onClick = { vm.startUpdate(updateDialog) }) { Chevron() }
                }
                UpdateState.Downloading -> {
                    Eyebrow("Update")
                    SettingRow("Updating Caddis", "Downloading in the background. Keep using the app.") { }
                }
                UpdateState.Ready -> {
                    Eyebrow("Update")
                    SettingRow("Restart to update", "The new version is ready.", onClick = vm::restartToUpdate) { Chevron() }
                }
                UpdateState.None -> Unit
            }
            Eyebrow("Preferences")
            SettingRow("Return rules", onClick = { navigate("rules") }) { Chevron() }
            SettingRow("Intents", onClick = { navigate("intents") }) { Fine(intents.size.toString()); Chevron() }
            SettingRow("Notifications", onClick = { navigate("notifications") }) { Chevron() }
            Eyebrow("Background work")
            SettingRow("Pause on low battery") { Toggle(st.pauseOnLowBattery, "Pause on low battery", vm::setPauseBattery) }
            Eyebrow("Your data")
            SettingRow("Export and cache", onClick = { navigate("data") }) { Chevron() }
            SettingRow("Trash", onClick = { navigate("trash") }) { Chevron() }
            SettingRow("Delete all data", onClick = { navigate("account") }) { Chevron() }
            Eyebrow("Privacy")
            SettingRow("Privacy policy", onClick = { navigate("privacy") }) { Chevron() }
        }
    }
}

/** Screen 22: Notifications (BRB-04, WK-02). */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    val daily = rememberNotificationToggle { vm.setDaily(it) }
    val weekly = rememberNotificationToggle { vm.setWeekly(it) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Notifications", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            SettingRow("Daily reminder", "At most one a day") { Toggle(st.dailyReminder, "Daily reminder") { daily(it) } }
            SettingRow("Quiet hours", "No reminders in this window", onClick = vm::cycleQuiet) { Text(st.quietHours.label, style = CaddisType.strong, color = C.colors.fg) }
            SettingRow("Weekly summary", "Your week, on Sunday") { Toggle(st.weeklySummary, "Weekly summary") { weekly(it) } }
            Eyebrow("What a reminder looks like")
            CCard(Modifier.fillMaxWidth().alpha(if (st.dailyReminder) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(C.colors.accentTint).border(1.5.dp, C.colors.accent, CircleShape))
                    Fine("Caddis · now")
                }
                Text(if (st.dailyReminder) "Something you saved that fits now" else "Reminders are off", style = CaddisType.strong, color = C.colors.fg, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                Fine(if (st.dailyReminder) "Intent · length · fits this moment" else "Turn on the daily reminder to see one.")
            }
        }
    }
}

/** Screen 24: return rules per intent (INT-04). */
@Composable
fun RulesScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(Intents.COOK) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Return rules", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            Fine("Tap an intent to change when its saves come back.", Modifier.padding(bottom = 6.dp))
            intents.forEach { row ->
                val r = rules[row.intent.id]
                SettingRow(row.intent.name, onClick = { if (r != null) open = if (open == row.intent.id) null else row.intent.id }) {
                    Fine(r.label()); if (r != null) Chevron()
                }
                if (r != null && open == row.intent.id) RuleEditor(r, vm::updateRule)
            }
        }
    }
}

@Composable
private fun RuleEditor(r: IntentRule, onChange: (IntentRule) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    CCard(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), feature = true) {
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

/** Screen 27: create, rename and delete intents; set the Done label (INT-05). */
@Composable
fun IntentsScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
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

/** Screen 25: export (ACC-02) and the thumbnail cache (MET-04). */
@Composable
fun DataScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val bytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshCache() }
    val mb = bytes / (1024 * 1024)
    val cap = CaddisApp.THUMB_CACHE_BYTES / (1024 * 1024)
    Column(Modifier.fillMaxSize()) {
        TopBar("Your data", onBack)
        Column(Modifier.padding(horizontal = 18.dp)) {
            CCard(Modifier.fillMaxWidth()) {
                Text("Export your library", style = CaddisType.strong, color = C.colors.fg)
                Fine("The whole library, including notes and collections.", Modifier.padding(top = 4.dp, bottom = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CButton("JSON", onClick = { vm.export(true) { context.startActivity(it) } }, modifier = Modifier.weight(1f))
                    CButton("HTML bookmarks", onClick = { vm.export(false) { context.startActivity(it) } }, modifier = Modifier.weight(1f))
                }
            }
            CCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Thumbnail cache", style = CaddisType.strong, color = C.colors.fg)
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.colors.tint)) {
                    Box(Modifier.fillMaxWidth((bytes.toFloat() / CaddisApp.THUMB_CACHE_BYTES).coerceIn(0f, 1f)).height(8.dp).background(C.colors.accent))
                }
                Fine("$mb MB of $cap MB")
                CChip("Clear cache", onClick = vm::clearCache, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** Screen 28 (+26): delete all data. Route `account` (there is no account; data lives on this phone). */
@Composable
fun AccountScreen(onBack: () -> Unit, onDeleted: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    var ask by remember { mutableStateOf(false) }
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Delete all data", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            CCard(Modifier.fillMaxWidth(), borderColor = c.crit, onClick = { ask = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Delete it all", style = CaddisType.strong, color = c.crit)
                        Fine("Removes your saves, notes and collections from this phone.")
                    }
                    Chevron()
                }
            }
            Fine("Export your library first if you want a copy.", Modifier.padding(top = 10.dp))
        }
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            containerColor = c.surface,
            title = { Text("Delete all your data?", style = CaddisType.sheetTitle, color = c.fg) },
            text = { Text("This deletes everything Caddis keeps on this phone: saves, notes and collections. Export first if you want a copy.", style = CaddisType.body, color = c.fg2) },
            confirmButton = { CButton("Delete everything", onClick = { ask = false; vm.deleteAll(onDeleted) }, style = BtnStyle.Danger) },
            dismissButton = { CButton("Cancel", onClick = { ask = false }) },
        )
    }
}

/** Screen 29: the short privacy policy (ACC-04). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Privacy policy", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            Sub("A short version of how Caddis treats your saves. Last updated 10 November 2026.", style = CaddisType.body)
            Point("Your library is yours", "Saves, notes, reasons and collections stay on this phone. There are no accounts and no Caddis server, so the developers never receive them.")
            Point("No ads, no analytics, no selling data", "Caddis has no ads, no analytics and does not sell or share what you save.")
            Point("Details come straight from the source", "To show a title and thumbnail, your phone asks the site you saved directly, like any web request. Only that link is requested, with no cookies, logins or third-party proxy. The site can see your IP address, and its own privacy policy applies.")
            Point("Sharing", "Caddis gets links through Android's share sheet and may note which app sent them. When you share a save out, only its title and a cleaned link go with it, never your reason, notes or collections.")
            Point("The clipboard", "Caddis reads it only after you tap Add, never in the background.")
            Point("Notifications", "Off by default. If you turn them on, at most one a day, created on your phone and never during quiet hours.")
            Point("Search stays on your phone", "Searching your library runs on the device, so it works offline.")
            Point("No AI", "No language model reads your saves, on the phone or anywhere else.")
            Point("Backups", "Android may back up Caddis to your Google account or copy it to a new phone. That is handled by Android and Google, and you can turn it off in system settings.")
            Point("You stay in control", "Deleted saves stay in Trash for 30 days. Export everything or delete all your data any time from Profile. Uninstalling removes it all.")
            CButton("Read the full policy", onClick = {
                val url = FULL_POLICY_URL
                if (url == null) {
                    android.widget.Toast.makeText(context, "The full policy will be published before release.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 22.dp))
        }
    }
}

/** The full policy, hosted as a GitHub gist (ACC-04). Null shows a "will be published" toast instead. */
private val FULL_POLICY_URL: String? = "https://gist.github.com/Priyanshu-git/b8b5b7a228afc6273df47f51f3e0fae2"

@Composable
private fun Point(title: String, body: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, style = CaddisType.strong, color = C.colors.fg)
        Sub(body, Modifier.padding(top = 2.dp), style = CaddisType.body)
    }
}
