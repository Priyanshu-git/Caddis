package com.nexxlabs.caddis.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import com.nexxlabs.caddis.BuildConfig
import com.nexxlabs.caddis.CaddisApp
import com.nexxlabs.caddis.ui.components.AppMark
import com.nexxlabs.caddis.ui.components.BigTitle
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CCard
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.Eyebrow
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.Sub
import com.nexxlabs.caddis.ui.components.TopBar
import com.nexxlabs.caddis.ui.settings.SettingsViewModel.CheckStatus
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import com.nexxlabs.caddis.update.UpdateState

private fun reminderSummary(daily: Boolean, weekly: Boolean) = when {
    daily && weekly -> "Daily and weekly"
    daily -> "Daily on"
    weekly -> "Weekly on"
    else -> "Off"
}

/** Screen 23: Settings hub. A short list: what comes back, reminders, a nudge to rate or share, then data and about. Everything else is one tap down. */
@Composable
fun SettingsScreen(navigate: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val intents by vm.intents.collectAsStateWithLifecycle()
    val trash by vm.trashCount.collectAsStateWithLifecycle()
    val bytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val st = s ?: return
    val updateDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshCache() }
    Column(Modifier.fillMaxSize()) {
        BigTitle("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 100.dp)) {
            UpdateBanner(update, onUpdate = { vm.startUpdate(updateDialog) }, onRestart = vm::restartToUpdate)
            SettingsGroup {
                NavRow(CIcons.Tag, "Intents", { navigate("intents") }, subtitle = "Why you saved it", value = intents.size.toString())
                GroupDivider()
                NavRow(CIcons.BringBack, "Return rules", { navigate("rules") }, subtitle = "When each intent comes back")
                GroupDivider()
                NavRow(CIcons.Bell, "Reminders", { navigate("reminders") }, subtitle = reminderSummary(st.dailyReminder, st.weeklySummary))
            }
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                Column(Modifier.padding(14.dp)) {
                    Text("Enjoying Caddis?", style = CaddisType.strong, color = C.colors.fg)
                    Fine("Tell someone, or tell us.")
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction(CIcons.Star, "Rate", "Rate Caddis on Google Play") {
                            context.launchOrTell(AppLinks.rate()) { vm.tell("Couldn't open Google Play.") }
                        }
                        QuickAction(CIcons.Share, "Share", "Share Caddis") {
                            context.launchOrTell(AppLinks.share()) { vm.tell("Nothing on this phone can share it.") }
                        }
                        QuickAction(CIcons.Mail, "Feedback", "Send feedback by email") {
                            context.launchOrTell(AppLinks.feedback()) { vm.tell("No email app found. Write to ${AppLinks.FEEDBACK_EMAIL}.") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                NavRow(
                    CIcons.Phone, "Your data", { navigate("data") },
                    subtitle = "${bytes / (1024 * 1024)} MB cached · " + if (trash == 0) "Trash is empty" else "$trash in Trash",
                )
                GroupDivider()
                NavRow(CIcons.Info, "About us", { navigate("about") }, subtitle = "Version, updates, privacy")
            }
        }
    }
}

/** The one loud thing on the hub, shown only while Google Play has something to install. */
@Composable
private fun UpdateBanner(update: UpdateState, onUpdate: () -> Unit, onRestart: () -> Unit) {
    val c = C.colors
    val title: String
    val sub: String
    val action: String?
    val click: (() -> Unit)?
    when (update) {
        is UpdateState.Available -> { title = "Update Caddis"; sub = "A newer version is on Google Play."; action = "Update"; click = onUpdate }
        UpdateState.Downloading -> { title = "Updating Caddis"; sub = "Downloading in the background. Keep using the app."; action = null; click = null }
        UpdateState.Ready -> { title = "Restart to update"; sub = "The new version is ready."; action = "Restart"; click = onRestart }
        UpdateState.None -> return
    }
    CCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), feature = true, onClick = click) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            IconTile(CIcons.Download, tint = c.onAccent, background = c.accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = CaddisType.strong, color = c.fg)
                Fine(sub)
            }
            if (action != null) {
                Text(
                    action, style = CaddisType.buttonSm, color = c.onAccent,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(c.accent).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Reminders: the two opt-in nudges, and what one looks like. */
@Composable
fun RemindersScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    val daily = rememberNotificationToggle { vm.setDaily(it) }
    val weekly = rememberNotificationToggle { vm.setWeekly(it) }
    val allowed = rememberNotificationsEnabled()
    val context = LocalContext.current
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Reminders", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            if (!allowed && (st.dailyReminder || st.weeklySummary)) {
                CCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), borderColor = c.warn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(CIcons.BellOff, null, tint = c.warn, modifier = Modifier.size(22.dp))
                        Column {
                            Text("Notifications are blocked", style = CaddisType.strong, color = c.fg)
                            Fine("Android is stopping Caddis from sending reminders.", Modifier.padding(top = 2.dp, bottom = 8.dp))
                            Text(
                                "Open Android settings", style = CaddisType.strong, color = c.accent,
                                modifier = Modifier.clickable {
                                    context.launchOrTell(AppLinks.notificationSettings(context.packageName)) {
                                        vm.tell("Open Android settings, then Apps, then Caddis.")
                                    }
                                },
                            )
                        }
                    }
                }
            }
            SettingsGroup {
                ToggleRow(CIcons.Bell, "Daily reminder", "At most one a day, when something fits", st.dailyReminder) { daily(it) }
                GroupDivider()
                ToggleRow(CIcons.CalendarWeek, "Weekly summary", "Your week, on Sunday evening", st.weeklySummary) { weekly(it) }
            }
            Eyebrow("What a reminder looks like")
            CCard(Modifier.fillMaxWidth().alpha(if (st.dailyReminder) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(c.accentTint).border(1.5.dp, c.accent, CircleShape))
                    Fine("Caddis · now")
                }
                Text(if (st.dailyReminder) "Something you saved that fits now" else "Reminders are off", style = CaddisType.strong, color = c.fg, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                Fine(if (st.dailyReminder) "Intent · length · fits this moment" else "Turn on the daily reminder to see one.")
            }
            Fine("Reminders are made on your phone. Nothing is sent anywhere.", Modifier.padding(top = 12.dp, start = 2.dp))
        }
    }
}

/** Your data: export, Trash, background work, and the one irreversible action, kept apart at the bottom. */
@Composable
fun YourDataScreen(onBack: () -> Unit, navigate: (String) -> Unit, onDeleted: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val bytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val trash by vm.trashCount.collectAsStateWithLifecycle()
    val st = s ?: return
    var ask by remember { mutableStateOf(false) }
    val c = C.colors
    LaunchedEffect(Unit) { vm.refreshCache() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Your data", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            Eyebrow("Your library", Modifier.padding(top = 6.dp, bottom = 8.dp))
            SettingsGroup {
                NavRow(CIcons.Download, "Export and cache", { navigate("export") }, subtitle = "JSON, bookmarks, thumbnails", value = "${bytes / (1024 * 1024)} MB")
                GroupDivider()
                NavRow(CIcons.Trash, "Trash", { navigate("trash") }, subtitle = "Kept for 30 days", value = trash.toString())
            }
            Eyebrow("Background")
            SettingsGroup {
                ToggleRow(CIcons.Battery, "Pause on low battery", "Skip fetching titles and thumbnails", st.pauseOnLowBattery) { vm.setPauseBattery(it) }
            }
            CButton("Delete all data", onClick = { ask = true }, style = BtnStyle.Danger, modifier = Modifier.fillMaxWidth().padding(top = 26.dp))
            Fine("Removes your saves, notes and collections from this phone. Export first if you want a copy.", Modifier.fillMaxWidth().padding(top = 8.dp), align = TextAlign.Center)
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

/** Export and cache: take a copy of the library, and see or clear the thumbnail cache (MET-04, ACC-02). */
@Composable
fun ExportScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val bytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshCache() }
    val mb = bytes / (1024 * 1024)
    val cap = CaddisApp.THUMB_CACHE_BYTES / (1024 * 1024)
    val c = C.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Export and cache", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            CCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                Text("Export your library", style = CaddisType.strong, color = c.fg)
                Fine("The whole library, including notes and collections.", Modifier.padding(top = 3.dp, bottom = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CButton("JSON", onClick = { vm.export(true) { context.startActivity(it) } }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                    CButton("HTML bookmarks", onClick = { vm.export(false) { context.startActivity(it) } }, modifier = Modifier.weight(1f))
                }
            }
            CCard(Modifier.fillMaxWidth().padding(top = 12.dp), padding = 14.dp) {
                Text("Thumbnail cache", style = CaddisType.strong, color = c.fg)
                Box(Modifier.padding(top = 12.dp, bottom = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.tint)) {
                    Box(Modifier.fillMaxWidth((bytes.toFloat() / CaddisApp.THUMB_CACHE_BYTES).coerceIn(0f, 1f)).height(8.dp).background(c.accent))
                }
                Fine("$mb MB of $cap MB. Thumbnails reload when you need them.", Modifier.padding(bottom = 12.dp))
                CButton("Clear cache", onClick = vm::clearCache, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** About us: the app, its version, whether it is current, the privacy policy, and who made it. */
@Composable
fun AboutScreen(onBack: () -> Unit, navigate: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val update by vm.update.collectAsStateWithLifecycle()
    val check by vm.checkStatus.collectAsStateWithLifecycle()
    val updateDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val c = C.colors
    // Only what Google Play really said: "Up to date" appears after a check that got an answer, never by default.
    val status: Pair<String, Color>? = when {
        update is UpdateState.Available -> "Update ready" to c.accent
        update == UpdateState.Downloading -> "Updating…" to c.fg2
        update == UpdateState.Ready -> "Restart to update" to c.accent
        check == CheckStatus.Checking -> "Checking…" to c.fg2
        check == CheckStatus.UpToDate -> "Up to date" to c.good
        check == CheckStatus.Unreachable -> "Couldn't check" to c.warn
        else -> null
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("About us", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppMark(72.dp)
                Text("Caddis", style = CaddisType.h2, color = c.fg, modifier = Modifier.padding(top = 14.dp))
                Fine("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.padding(top = 2.dp))
            }
            SettingsGroup {
                NavRow(CIcons.Refresh, "Check for updates", { vm.checkForUpdate(updateDialog) }, trailing = { status?.let { (text, color) -> StatusPill(text, color) } })
                GroupDivider()
                NavRow(CIcons.Shield, "Privacy policy", { navigate("privacy") })
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Made by Nexxlabs", style = CaddisType.strong, color = c.fg2)
            Fine(AppLinks.FEEDBACK_EMAIL, Modifier.padding(top = 4.dp))
        }
    }
}

/** Screen 29: the short privacy policy (ACC-04). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Privacy policy", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 40.dp)) {
            Sub("A short version of how Caddis treats your saves. Last updated 10 November 2026.", Modifier.padding(bottom = 14.dp), style = CaddisType.body)
            SettingsGroup {
                POINTS.forEachIndexed { i, (title, body) ->
                    if (i > 0) GroupDivider(14.dp)
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(title, style = CaddisType.strong, color = C.colors.fg)
                        Sub(body, Modifier.padding(top = 2.dp), style = CaddisType.body)
                    }
                }
            }
            CButton("Read the full policy", onClick = {
                val url = FULL_POLICY_URL
                if (url == null) {
                    android.widget.Toast.makeText(context, "The full policy will be published before release.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        }
    }
}

private val POINTS = listOf(
    "Your library is yours" to "Saves, notes, reasons and collections stay on this phone. There are no accounts and no Caddis server, so the developers never receive them.",
    "No ads, no analytics, no selling data" to "Caddis has no ads, no analytics and does not sell or share what you save.",
    "Details come straight from the source" to "To show a title and thumbnail, your phone asks the site you saved directly, like any web request. Only that link is requested, with no cookies, logins or third-party proxy. The site can see your IP address, and its own privacy policy applies.",
    "Sharing" to "Caddis gets links through Android's share sheet and may note which app sent them. When you share a save out, only its title and a cleaned link go with it, never your reason, notes or collections.",
    "The clipboard" to "Caddis reads it only after you tap Add, never in the background.",
    "Notifications" to "Off by default. If you turn them on, at most one a day, created on your phone.",
    "Search stays on your phone" to "Searching your library runs on the device, so it works offline.",
    "No AI" to "No language model reads your saves, on the phone or anywhere else.",
    "Backups" to "Android may back up Caddis to your Google account or copy it to a new phone. That is handled by Android and Google, and you can turn it off in system settings.",
    "You stay in control" to "Deleted saves stay in Trash for 30 days. Export everything or delete all your data any time from Settings. Uninstalling removes it all.",
)

/** The full policy, hosted as a GitHub gist (ACC-04). Null shows a "will be published" toast instead. */
private val FULL_POLICY_URL: String? = "https://gist.github.com/Priyanshu-git/b8b5b7a228afc6273df47f51f3e0fae2"
