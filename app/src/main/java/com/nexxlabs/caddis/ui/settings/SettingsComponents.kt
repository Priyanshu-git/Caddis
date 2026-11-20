package com.nexxlabs.caddis.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexxlabs.caddis.ui.components.CCard
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.Dot
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.Toggle
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType

/** Asks for the notification permission (Android 13+) only when the person turns a reminder on. */
@Composable
internal fun rememberNotificationToggle(onResult: (Boolean) -> Unit): (Boolean) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onResult(granted) }
    return { on ->
        if (on && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(on)
    }
}

/** True while Android lets Caddis post notifications; read again whenever the app comes back to the front. */
@Composable
internal fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

/** Starts [intent]; when nothing on the phone can handle it, runs [orElse] instead of crashing. */
internal fun Context.launchOrTell(intent: Intent, orElse: () -> Unit) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { orElse() }
}

/** A settings row without the divider, for rows inside a card (used by onboarding). */
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

/** One rounded card that holds a run of rows; put a [GroupDivider] between them. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    CCard(modifier.fillMaxWidth(), padding = 0.dp, content = content)

/** The hairline between two rows of a group, starting after the icon tile. */
@Composable
internal fun GroupDivider(inset: Dp = 55.dp) = HorizontalDivider(Modifier.padding(start = inset), color = C.colors.line)

/** The small rounded square that carries a row's icon. Neutral, so the accent stays for what is on or active. */
@Composable
internal fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = C.colors.fg2, background: Color = C.colors.tint) {
    Box(modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * A row that opens something: an icon tile, a title with an optional line under it, an optional [value]
 * and a chevron. [external] swaps the chevron for the "leaves the app" arrow.
 */
@Composable
internal fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    external: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = C.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = CaddisType.strong, color = c.fg)
            if (subtitle != null) Fine(subtitle)
        }
        if (value != null) Text(value, style = CaddisType.sub, color = c.fg2)
        if (trailing != null) trailing() else Icon(if (external) CIcons.ExternalLink else CIcons.Chevron, null, tint = c.fg2, modifier = Modifier.size(18.dp))
    }
}

/** A row with an on/off switch. The whole row toggles; the switch is announced by [title]. */
@Composable
internal fun ToggleRow(icon: ImageVector, title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!on) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = CaddisType.strong, color = C.colors.fg)
            Fine(subtitle)
        }
        Toggle(on, title, onChange)
    }
}

/** A small status: a coloured dot and a word, for example "Up to date". */
@Composable
internal fun StatusPill(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(C.colors.tint).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Dot(color)
        Text(text, style = CaddisType.fine, color = color)
    }
}

/** One of the three buttons in "Enjoying Caddis?". */
@Composable
internal fun RowScope.QuickAction(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    val c = C.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 64.dp)
            .clip(shape)
            .background(c.bg)
            .border(1.dp, c.line, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Text(label, style = CaddisType.tag, color = c.fg, textAlign = TextAlign.Center)
    }
}
