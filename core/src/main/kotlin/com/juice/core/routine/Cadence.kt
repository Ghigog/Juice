package com.juice.core.routine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The days of the week a Routine repeats on, with an optional [time] of day. All seven days means daily. */
data class Cadence(val days: Set<DayOfWeek>, val time: LocalTime? = null) {
    init {
        require(days.isNotEmpty()) { "A cadence needs at least one day" }
    }

    fun isDue(date: LocalDate): Boolean = date.dayOfWeek in days

    /** The first due day strictly after [date]. */
    fun nextDueAfter(date: LocalDate): LocalDate = firstDueFrom(date.plusDays(1))

    /** The first due day on or after [date]. */
    fun firstDueFrom(date: LocalDate): LocalDate = generateSequence(date) { it.plusDays(1) }.first { isDue(it) }

    /** The moment the routine is due on [date] in [zone], or null when it has no time. */
    fun deadlineOn(date: LocalDate, zone: ZoneId): Instant? =
        time?.let { date.atTime(it).atZone(zone).toInstant() }

    /** "Every day", "Every Sun", "Every Mon, Thu". */
    fun label(): String =
        if (days.size == DayOfWeek.entries.size) {
            "Every day"
        } else {
            days.sorted().joinToString(", ", "Every ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        }
}
