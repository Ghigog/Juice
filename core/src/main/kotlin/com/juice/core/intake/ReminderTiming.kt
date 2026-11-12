package com.juice.core.intake

import java.time.LocalTime
import java.time.ZonedDateTime

private val EARLY_CUTOFF: LocalTime = LocalTime.of(10, 0)
private val MIDDAY_CUTOFF: LocalTime = LocalTime.of(17, 0)
private val MORNING: LocalTime = LocalTime.of(9, 0)
private val EVENING: LocalTime = LocalTime.of(20, 0)

/** The time of day a deadline gets when the person named only a date. */
internal val DATE_ONLY_TIME: LocalTime = LocalTime.of(23, 59)

/**
 * When to remind about [deadline]: the evening before an early one (before 10:00), the morning of a midday one
 * (10:00 to before 17:00), two hours before a late one, 09:00 that day for a date with no time ([hasTime] false).
 * A moment that is not after [now] falls back to an hour before the deadline; null when that has passed too.
 */
fun reminderFor(deadline: ZonedDateTime, now: ZonedDateTime, hasTime: Boolean): ZonedDateTime? {
    val date = deadline.toLocalDate()
    val time = deadline.toLocalTime()
    val chosen = when {
        !hasTime -> date.atTime(MORNING).atZone(deadline.zone)
        time < EARLY_CUTOFF -> date.minusDays(1).atTime(EVENING).atZone(deadline.zone)
        time < MIDDAY_CUTOFF -> date.atTime(MORNING).atZone(deadline.zone)
        else -> deadline.minusHours(2)
    }
    if (chosen.isAfter(now)) return chosen
    return deadline.minusHours(1).takeIf { it.isAfter(now) }
}
