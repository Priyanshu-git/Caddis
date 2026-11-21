package com.nexxlabs.caddis.ui.sheets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ReturnRule
import com.nexxlabs.caddis.domain.schedule.ItemReminder
import com.nexxlabs.caddis.notify.ReminderAlarms
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.components.CSheet
import com.nexxlabs.caddis.ui.components.Chips
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.settings.AppLinks
import com.nexxlabs.caddis.ui.settings.launchOrTell
import com.nexxlabs.caddis.ui.settings.rememberNotificationToggle
import com.nexxlabs.caddis.ui.settings.rememberNotificationsEnabled
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Screen 9: "Bring it back". Two modes: **Later** puts the save back on Home at a time that suits it (BRB-03),
 * **Remind me** sends a notification at the exact time the person picks and keeps it on Home until they act on it.
 */
@Composable
fun LaterSheet(
    item: Item,
    now: () -> ZonedDateTime,
    onPick: (ReturnRule, LocalDate?) -> Unit,
    onRemind: (Instant) -> Unit,
    onClearReminder: () -> Unit,
    onDismiss: () -> Unit,
) {
    // An item that already has a reminder reopens on it, ready to change or clear.
    var remind by rememberSaveable { mutableStateOf(item.hasReminder && item.returnAt?.isAfter(now().toInstant()) == true) }
    CSheet(onDismiss) {
        Text("Bring it back", style = CaddisType.sheetTitle, color = C.colors.fg)
        Fine(item.displayTitle, Modifier.padding(top = 2.dp))
        ModeSwitch(remind) { remind = it }
        Explainer(remind, item)
        if (remind) {
            RemindPane(item, now, onRemind, onClearReminder, onUseLater = { remind = false }, onDismiss)
        } else {
            LaterPane(now().toLocalDate(), onPick, onDismiss)
        }
    }
}

// ---------- the two modes ----------

@Composable
private fun ModeSwitch(remind: Boolean, onChange: (Boolean) -> Unit) {
    val c = C.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.tint)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        ModeTab("Later", !remind, Modifier.weight(1f)) { onChange(false) }
        ModeTab("Remind me", remind, Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = C.colors
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) c.surface else Color.Transparent)
            .border(1.dp, if (selected) c.line else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (selected) CaddisType.strong else CaddisType.body, color = if (selected) c.fg else c.fg2)
    }
}

/** One plain sentence about what the selected mode does, right under the switch. */
@Composable
private fun Explainer(remind: Boolean, item: Item) {
    val c = C.colors
    val text = if (remind) {
        "You’ll get a notification at the exact time you set. Tap it to open ${if (item.isNote) "the note" else "the link"}."
    } else {
        "Caddis puts this back on Home at the time you choose. No sound, no buzz."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (remind) c.accentTint else c.tint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (remind) CIcons.Bell else CIcons.House, null, tint = if (remind) c.accent else c.fg2, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text, style = CaddisType.body, color = c.fg)
    }
}

// ---------- Later ----------

@Composable
private fun LaterPane(today: LocalDate, onPick: (ReturnRule, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        LaterRow("Tonight", "7 pm") { onPick(ReturnRule.TONIGHT, null) }
        LaterRow("This weekend", "Saturday morning") { onPick(ReturnRule.WEEKEND, null) }
        LaterRow("Next week", "Monday") { onPick(ReturnRule.NEXT_WEEK, null) }
        LaterRow("Pick a date", "Calendar") { picking = true }
        LaterRow("Not again", "Stays in your Library") { onPick(ReturnRule.NEVER, null) }
        Fine("A date you pick won’t reappear before then.", Modifier.padding(top = 12.dp))
        CButton("Cancel", onClick = onDismiss, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
    }
    if (picking) {
        DayDialog(initial = today.plusDays(8), first = today.plusDays(1), onSet = { picking = false; onPick(ReturnRule.DATE, it) }, onCancel = { picking = false })
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

// ---------- Remind me ----------

@Composable
private fun RemindPane(item: Item, clockNow: () -> ZonedDateTime, onRemind: (Instant) -> Unit, onClear: () -> Unit, onUseLater: () -> Unit, onDismiss: () -> Unit) {
    val c = C.colors
    val context = LocalContext.current
    // The clock keeps ticking while the sheet is open, so "in 1 h 20 min" and "already gone by" stay true.
    var now by remember { mutableStateOf(clockNow()) }
    val zone: ZoneId = now.zone
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = clockNow()
        }
    }

    val existing = item.returnAt?.takeIf { item.hasReminder && it.isAfter(now.toInstant()) }
    val start = remember { existing?.atZone(zone) ?: ItemReminder.defaultFor(now) }
    var dayEpoch by rememberSaveable { mutableLongStateOf(start.toLocalDate().toEpochDay()) }
    var minute by rememberSaveable { mutableIntStateOf(start.hour * 60 + start.minute) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }

    val today = now.toLocalDate()
    val time = LocalTime.of(minute / 60, minute % 60)
    val picked = ItemReminder.resolve(maxOf(LocalDate.ofEpochDay(dayEpoch), today), time, now)
    val pickedAt = picked.at.atZone(zone)
    val shownDay = pickedAt.toLocalDate()
    val third = today.plusDays(2)
    val customDay = shownDay.isAfter(third)

    val notificationsOk = rememberNotificationsEnabled()
    val exactOk = rememberExactAlarmsAllowed()
    val openNotificationSettings = { context.launchOrTell(AppLinks.notificationSettings(context.packageName)) {} }
    var askedAt by remember { mutableLongStateOf(0L) }
    // A "no" the person gave is theirs to keep. A "no" that comes back at once means Android won't ask again, so
    // the only way left is its settings page.
    val askForNotifications = rememberNotificationToggle { granted ->
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 400) openNotificationSettings()
    }

    Column {
        if (!notificationsOk) {
            Notice(CIcons.BellOff, "Android is blocking Caddis notifications, so a reminder can’t reach you. Turn them on and it will work.")
        }
        // The big time: tap it to change it.
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).alpha(if (notificationsOk) 1f else 0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
            val parts = ItemReminder.clock(pickedAt.toLocalTime()).split(' ')
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = "Change the time", onClick = { pickingTime = true })
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Reminder time ${parts.joinToString(" ")}. Tap to change." },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(parts[0], style = CaddisType.stat.copy(fontSize = 44.sp, lineHeight = 48.sp), color = c.fg)
                Text(" " + parts[1], style = CaddisType.stat.copy(fontSize = 22.sp, lineHeight = 36.sp), color = c.accent)
            }
            val dayLabel = when (shownDay) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> ItemReminder.shortDate(shownDay)
            }
            Fine("$dayLabel · ${ItemReminder.until(now.toInstant(), picked.at)}", Modifier.padding(top = 2.dp))
        }
        Chips(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            CChip("Today", selected = shownDay == today, onClick = { dayEpoch = today.toEpochDay() })
            CChip("Tomorrow", selected = shownDay == today.plusDays(1), onClick = { dayEpoch = today.plusDays(1).toEpochDay() })
            CChip(third.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), selected = shownDay == third, onClick = { dayEpoch = third.toEpochDay() })
            CChip(if (customDay) ItemReminder.shortDate(shownDay) else "Date", selected = customDay, leading = CIcons.CalendarWeek, onClick = { pickingDay = true })
        }
        if (picked.movedToNextDay) {
            Fine("${ItemReminder.clock(time)} has already gone by today, so we picked tomorrow.", Modifier.padding(top = 8.dp))
        }
        Fine("Quick times", Modifier.padding(top = 14.dp, bottom = 8.dp))
        Chips(Modifier.fillMaxWidth()) {
            CChip("In 1 hour", onClick = {
                val t = ItemReminder.inOneHour(now).atZone(zone)
                dayEpoch = t.toLocalDate().toEpochDay()
                minute = t.hour * 60 + t.minute
            })
            ItemReminder.QUICK_TIMES.forEach { q ->
                CChip(ItemReminder.clock(q), selected = time == q, onClick = { minute = q.hour * 60 + q.minute })
            }
        }
        if (notificationsOk && !exactOk) {
            Notice(
                CIcons.Alert,
                "Android may deliver this a few minutes late. Allow exact alarms for on-the-minute reminders.",
                Modifier.padding(top = 12.dp),
            )
        }

        val label = if (existing != null) "Update reminder" else if (shownDay == today) "Remind me at ${ItemReminder.clock(pickedAt.toLocalTime())}"
        else "Remind me " + ItemReminder.phrase(picked.at, now.toInstant(), zone)
        if (notificationsOk) {
            CButton(label, onClick = { onRemind(picked.at) }, style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
            if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                CButton("Allow exact alarms", onClick = {
                    context.launchOrTell(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    ) {}
                }, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
            }
            if (existing != null) CButton("Clear reminder", onClick = onClear, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
            CButton("Cancel", onClick = onDismiss, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
        } else {
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            CButton(
                "Allow notifications",
                onClick = {
                    if (needsPermission) {
                        askedAt = SystemClock.elapsedRealtime()
                        askForNotifications(true)
                    } else openNotificationSettings()
                },
                style = BtnStyle.Primary,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            CButton("Use Later instead", onClick = onUseLater, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
        }
    }

    if (pickingTime) {
        TimeDialog(initial = time, onSet = { minute = it.hour * 60 + it.minute; pickingTime = false }, onCancel = { pickingTime = false })
    }
    if (pickingDay) {
        DayDialog(
            initial = shownDay, first = today,
            onSet = { dayEpoch = it.toEpochDay(); pickingDay = false },
            onCancel = { pickingDay = false },
        )
    }
}

/** A calm note inside the sheet: a warning about notifications, or about timing. */
@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = C.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.tint)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = c.warn, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text, style = CaddisType.body, color = c.fg)
    }
}

/** True while Android 12+ lets Caddis set exact alarms; read again whenever the app comes back to the front. */
@Composable
private fun rememberExactAlarmsAllowed(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(ReminderAlarms.canScheduleExact(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = ReminderAlarms.canScheduleExact(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

// ---------- the system pickers ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onSet: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val c = C.colors
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24)
    val colors = TimePickerDefaults.colors(
        containerColor = c.surface,
        clockDialColor = c.tint,
        selectorColor = c.accent,
        periodSelectorBorderColor = c.line,
        clockDialSelectedContentColor = c.onAccent,
        clockDialUnselectedContentColor = c.fg,
        periodSelectorSelectedContainerColor = c.accentTint,
        periodSelectorUnselectedContainerColor = Color.Transparent,
        periodSelectorSelectedContentColor = c.accent,
        periodSelectorUnselectedContentColor = c.fg2,
        timeSelectorSelectedContainerColor = c.accentTint,
        timeSelectorUnselectedContainerColor = c.tint,
        timeSelectorSelectedContentColor = c.accent,
        timeSelectorUnselectedContentColor = c.fg,
    )
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.surface,
        title = { Text("Reminder time", style = CaddisType.sheetTitle, color = c.fg) },
        text = { TimePicker(state, colors = colors) },
        confirmButton = { TextButton(onClick = { onSet(LocalTime.of(state.hour, state.minute)) }) { Text("Set", color = c.accent) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = c.fg2) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDialog(initial: LocalDate, first: LocalDate, onSet: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val minUtc = first.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms == null) onCancel() else onSet(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text("Set", color = C.colors.accent) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = C.colors.fg2) } },
    ) { DatePicker(state = state) }
}
