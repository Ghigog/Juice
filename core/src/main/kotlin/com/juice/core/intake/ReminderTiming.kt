package com.juice.core.intake

import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime

private val EARLY_CUTOFF: LocalTime = LocalTime.of(10, 0)
private val MIDDAY_CUTOFF: LocalTime = LocalTime.of(17, 0)
private val MORNING: LocalTime = LocalTime.of(9, 0)
private val EVENING: LocalTime = LocalTime.of(20, 0)

/** The time of day a deadline gets when the person named only a date. */
val DATE_ONLY_TIME: LocalTime = LocalTime.of(23, 59)

/** The longest a reminder may come before its deadline. */
internal val MAX_REMINDER_LEAD: Duration = Duration.ofDays(7)

/** A deadline this many calendar days off or fewer is reminded by its time of day; a later one gets an earlier 09:00 reminder. */
private const val NEAR_DAYS = 3L

/**
 * When to remind about [deadline]. Within [NEAR_DAYS] days: the evening before an early one (before 10:00), the
 * morning of a midday one (10:00 to before 17:00), two hours before a late one, 09:00 that day for a date with no
 * time ([hasTime] false). Further off: 09:00 some days before, growing with the distance (a Task a quarter of it,
 * a Project, which is bigger, half of it) and never more than 7 days, so a Project two weeks away is reminded a week
 * ahead. A moment that is not after [now] falls back to an hour before the deadline, then to the deadline itself; null when that has passed too.
 */
fun reminderFor(deadline: ZonedDateTime, now: ZonedDateTime, hasTime: Boolean, type: ItemType = ItemType.Task): ZonedDateTime? {
    val date = deadline.toLocalDate()
    val time = deadline.toLocalTime()
    val daysOff = ChronoUnit.DAYS.between(now.toLocalDate(), date)
    val chosen = when {
        daysOff > NEAR_DAYS -> {
            val share = if (type == ItemType.Project) 2 else 4
            val lead = (daysOff / share).coerceIn(1, MAX_REMINDER_LEAD.toDays())
            date.minusDays(lead).atTime(MORNING).atZone(deadline.zone)
        }
        !hasTime -> date.atTime(MORNING).atZone(deadline.zone)
        time < EARLY_CUTOFF -> date.minusDays(1).atTime(EVENING).atZone(deadline.zone)
        time < MIDDAY_CUTOFF -> date.atTime(MORNING).atZone(deadline.zone)
        else -> deadline.minusHours(2)
    }
    if (chosen.isAfter(now)) return chosen
    val hourBefore = deadline.minusHours(1)
    return if (hourBefore.isAfter(now)) hourBefore else deadline.takeIf { it.isAfter(now) }
}
