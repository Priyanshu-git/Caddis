package com.nexxlabs.caddis.domain.model

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

/** The eight default intents (INT-01) and their default return rules (INT-04). */
object Intents {
    const val COOK = "cook"
    const val WATCH = "watch"
    const val READ = "read"
    const val LEARN = "learn"
    const val TRY = "try"
    const val BUY = "buy"
    const val GO = "go"
    const val KEEP = "keep"

    val WEEKDAYS: Set<DayOfWeek> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
    val WEEKEND: Set<DayOfWeek> = setOf(SATURDAY, SUNDAY)
    val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()

    val defaults: List<Intent> = listOf(
        Intent(COOK, "Cook", "Made it", true, 0),
        Intent(WATCH, "Watch", "Watched", true, 1),
        Intent(READ, "Read", "Read", true, 2),
        Intent(LEARN, "Learn", "Learned", true, 3),
        Intent(TRY, "Try", "Tried", true, 4),
        Intent(BUY, "Buy", "Bought", true, 5),
        Intent(GO, "Go there", "Visited", true, 6),
        Intent(KEEP, "Just keep", "Done", true, 7),
    )

    val defaultRules: List<IntentRule> = listOf(
        IntentRule(COOK, WEEKEND, TimeOfDay.MORNING),
        IntentRule(WATCH, ALL_DAYS, TimeOfDay.EVENING),
        IntentRule(READ, ALL_DAYS, TimeOfDay.AFTERNOON),
        IntentRule(LEARN, WEEKDAYS, TimeOfDay.MORNING),
        IntentRule(TRY, WEEKEND, TimeOfDay.AFTERNOON),
        IntentRule(BUY, WEEKDAYS, TimeOfDay.EVENING),
        IntentRule(GO, WEEKEND, TimeOfDay.MORNING),
        // Just keep has no rule: it stays where it is.
    )

    /** INT-05: a new intent returns on weekday afternoons, at most once a day. */
    fun ruleForNewIntent(intentId: String) = IntentRule(intentId, WEEKDAYS, TimeOfDay.AFTERNOON, 1)

    /** Analytics may only name default intents; custom ones are sent as "custom" (tech arch 1.3). */
    fun analyticsId(intentId: String): String =
        if (defaults.any { it.id == intentId }) intentId else "custom"
}

/** Human label for a rule, matching the prototype ("Weekend mornings", "Weekday afternoons"). */
fun IntentRule?.label(): String {
    if (this == null) return "Stays where it is"
    if (days.isEmpty()) return "Never returns"
    val t = timeOfDay.label.lowercase()
    return when {
        days == Intents.WEEKEND -> "Weekend ${t}s"
        days == Intents.WEEKDAYS -> "Weekday ${t}s"
        days == Intents.ALL_DAYS -> "${timeOfDay.label}s"
        else -> DayOfWeek.entries.filter { it in days }
            .joinToString(", ") { it.name.take(1) + it.name.substring(1, 3).lowercase() } + " · " + timeOfDay.label
    }
}
