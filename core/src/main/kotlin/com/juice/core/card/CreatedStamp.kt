package com.juice.core.card

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * When a card was created, for its caption line: "Today 14:05", "Yesterday 09:30", "3 Nov 14:05" earlier this year
 * and "3 Nov 2025" in an earlier year. Day and time are read in [zone]; [today] is the viewer's current date.
 */
fun createdStamp(createdAt: Instant, zone: ZoneId, today: LocalDate): String {
    val at = createdAt.atZone(zone)
    val day = at.toLocalDate()
    return when {
        day == today -> "Today ${time.format(at)}"
        day == today.minusDays(1) -> "Yesterday ${time.format(at)}"
        day.year == today.year -> "${dayMonth.format(at)} ${time.format(at)}"
        else -> dayMonthYear.format(at)
    }
}
