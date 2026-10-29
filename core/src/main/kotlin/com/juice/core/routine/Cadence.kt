package com.juice.core.routine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The days of the week a Routine repeats on, every [intervalWeeks] weeks, with an optional [time] of day.
 * All seven days means daily. With [intervalDays] above one it repeats every that many days from its anchor and [days] is ignored.
 */
data class Cadence(val days: Set<DayOfWeek>, val time: LocalTime? = null, val intervalWeeks: Int = 1, val intervalDays: Int = 1) {
    init {
        require(days.isNotEmpty()) { "A cadence needs at least one day" }
        require(intervalWeeks >= 1) { "The interval must be at least one week" }
        require(intervalDays >= 1) { "The interval must be at least one day" }
        require(intervalDays == 1 || intervalWeeks == 1) { "A day interval and a week interval cannot be combined" }
    }

    /** With a day interval the anchor day (the Routine's nextDueOn) decides, so every date is due. */
    fun isDue(date: LocalDate): Boolean = intervalDays > 1 || date.dayOfWeek in days

    /** The first due day strictly after [date]: later this week if any, else in the week [intervalWeeks] on. */
    fun nextDueAfter(date: LocalDate): LocalDate {
        if (intervalDays > 1) return date.plusDays(intervalDays.toLong())
        return generateSequence(date.plusDays(1)) { it.plusDays(1) }
            .takeWhile { it.dayOfWeek != DayOfWeek.MONDAY }
            .firstOrNull { isDue(it) }
            ?: firstDueFrom(date.with(DayOfWeek.MONDAY).plusWeeks(intervalWeeks.toLong()))
    }

    /** The first due day on or after [date]. */
    fun firstDueFrom(date: LocalDate): LocalDate = generateSequence(date) { it.plusDays(1) }.first { isDue(it) }

    /** The moment the routine is due on [date] in [zone], or null when it has no time. */
    fun deadlineOn(date: LocalDate, zone: ZoneId): Instant? =
        time?.let { date.atTime(it).atZone(zone).toInstant() }

    /** "Every day", "Every Sun", "Every Mon, Thu", "Every 2 weeks on Fri", "Every 3 days". */
    fun label(): String {
        val names = days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        return when {
            intervalDays > 1 -> "Every $intervalDays days"
            intervalWeeks > 1 -> "Every $intervalWeeks weeks on $names"
            days.size == DayOfWeek.entries.size -> "Every day"
            else -> "Every $names"
        }
    }
}
