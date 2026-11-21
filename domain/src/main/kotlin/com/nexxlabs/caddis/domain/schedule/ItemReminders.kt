package com.nexxlabs.caddis.domain.schedule

import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.ReturnRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * "Remind me": a notification for one save at a moment the person chose, such as 11 pm tonight. These are
 * the rules for picking and describing that moment; Android's alarm clock and the notification live in `:app`.
 */
object ItemReminder {
    /** The times offered under the clock besides "in 1 hour". */
    val QUICK_TIMES: List<LocalTime> = listOf(LocalTime.of(9, 0), LocalTime.of(19, 0), LocalTime.of(23, 0))

    /** The reminder that "Tomorrow" on the notification sets. */
    val TOMORROW_TIME: LocalTime = LocalTime.of(9, 0)

    /** A reminder that arrives later than this after its time is described by its clock time, not "tonight". */
    val LATE_AFTER: Duration = Duration.ofMinutes(30)

    /** A reminder for [at]; [movedToNextDay] when [time] had already passed today and tomorrow was used instead. */
    data class Resolved(val at: Instant, val movedToNextDay: Boolean)

    /**
     * The moment for [time] on [day]. Only today can be in the past; then it rolls to tomorrow and says so,
     * the way "Tonight" does. A reminder is always at least a minute away.
     */
    fun resolve(day: LocalDate, time: LocalTime, now: ZonedDateTime): Resolved {
        val minimum = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val at = day.atTime(time).atZone(now.zone)
        return if (at.isBefore(minimum)) Resolved(day.plusDays(1).atTime(time).atZone(now.zone).toInstant(), true)
        else Resolved(at.toInstant(), false)
    }

    /** What the sheet shows when it opens: the next whole hour that is at least half an hour away. */
    fun defaultFor(now: ZonedDateTime): ZonedDateTime {
        val t = now.plusMinutes(30)
        val floor = t.truncatedTo(ChronoUnit.HOURS)
        return if (t == floor) floor else floor.plusHours(1)
    }

    fun inOneHour(now: ZonedDateTime): Instant = now.truncatedTo(ChronoUnit.MINUTES).plusHours(1).toInstant()

    fun tomorrowMorning(now: ZonedDateTime): Instant =
        now.toLocalDate().plusDays(1).atTime(TOMORROW_TIME).atZone(now.zone).toInstant()

    /** "11:00 pm", "9:05 am", "12:00 am". */
    fun clock(time: LocalTime): String {
        val h = time.hour % 12
        return "${if (h == 0) 12 else h}:${time.minute.toString().padStart(2, '0')} ${if (time.hour < 12) "am" else "pm"}"
    }

    /** "in 45 min", "in 3 h", "in 1 h 20 min", "in 2 days". */
    fun until(from: Instant, to: Instant): String {
        val minutes = Duration.between(from, to).toMinutes()
        return when {
            minutes < 1 -> "in under a minute"
            minutes < 60 -> "in $minutes min"
            minutes < 24 * 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "in $h h" else "in $h h $m min"
            }
            else -> {
                val days = minutes / (24 * 60)
                "in $days ${if (days == 1L) "day" else "days"}"
            }
        }
    }

    /** "tonight", "today", "tomorrow", or "Mon 24 Nov". */
    fun dayWord(at: Instant, now: Instant, zone: ZoneId): String {
        val d = at.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        return when (d.toLocalDate()) {
            today -> if (d.hour >= 17) "tonight" else "today"
            today.plusDays(1) -> "tomorrow"
            else -> shortDate(d.toLocalDate())
        }
    }

    /** "tonight at 11:00 pm", "tomorrow at 9:00 am", "on Mon 24 Nov at 9:00 am". Used in the toast. */
    fun phrase(at: Instant, now: Instant, zone: ZoneId): String {
        val d = at.atZone(zone)
        val word = dayWord(at, now, zone)
        val time = clock(d.toLocalTime())
        return if (word.first().isDigit() || word[0].isUpperCase()) "on $word at $time" else "$word at $time"
    }

    /** The short tag on a Library row or a Home card: "Tonight 11:00 pm", "Tomorrow 9:00 am", "Mon 24 Nov, 9:00 am". */
    fun tag(at: Instant, now: Instant, zone: ZoneId): String {
        val d = at.atZone(zone)
        val word = dayWord(at, now, zone)
        val time = clock(d.toLocalTime())
        return if (word[0].isUpperCase()) "$word, $time" else word.replaceFirstChar { it.uppercase() } + " " + time
    }

    /** The tag on a Library row, kept short so the title keeps its room: "Reminder 11:00 pm", "Reminder tomorrow", "Reminder 27 Nov". */
    fun rowTag(at: Instant, now: Instant, zone: ZoneId): String {
        val d = at.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        return when (d.toLocalDate()) {
            today -> "Reminder ${clock(d.toLocalTime())}"
            today.plusDays(1) -> "Reminder tomorrow"
            else -> "Reminder ${d.dayOfMonth} ${d.month.name.take(1)}${d.month.name.substring(1, 3).lowercase()}"
        }
    }

    /** The second line of the notification: "You asked to be reminded tonight." Late ones name the clock time instead. */
    fun notificationText(at: Instant, firedAt: Instant, zone: ZoneId): String {
        if (Duration.between(at, firedAt) > LATE_AFTER) {
            return "You asked to be reminded at ${clock(at.atZone(zone).toLocalTime())}."
        }
        val hour = at.atZone(zone).hour
        val part = when (hour) {
            in 5..11 -> "this morning"
            in 12..16 -> "this afternoon"
            else -> "tonight"
        }
        return "You asked to be reminded $part."
    }

    /** "Mon 24 Nov" (English, so it never depends on the phone's language settings). */
    fun shortDate(d: LocalDate): String {
        val dow = d.dayOfWeek.name.take(1) + d.dayOfWeek.name.substring(1, 3).lowercase()
        val mon = d.month.name.take(1) + d.month.name.substring(1, 3).lowercase()
        return "$dow ${d.dayOfMonth} $mon"
    }
}

/**
 * Which reminders are due, and when the next one is. [after] is the last moment already handled, kept by the
 * phone, so a reminder that came due while the phone was off or the app was stopped still goes out once.
 */
object ReminderSweep {
    private fun pending(items: List<Item>): List<Item> = items.filter {
        it.status == ItemStatus.ACTIVE && it.returnRule == ReturnRule.REMINDER && it.returnAt != null
    }

    /** Reminders whose time is after [after] and not after [now], earliest first. */
    fun due(items: List<Item>, after: Instant, now: Instant): List<Item> =
        pending(items).filter { val at = it.returnAt!!; at.isAfter(after) && !at.isAfter(now) }.sortedBy { it.returnAt }

    /** The earliest reminder time after [after], or null when nothing is waiting. */
    fun next(items: List<Item>, after: Instant): Instant? =
        pending(items).map { it.returnAt!! }.filter { it.isAfter(after) }.minOrNull()
}
