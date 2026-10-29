package com.juice.core.intake

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Pure title rewrites for chip edits. Each returns the new title only: callers re-run classification
 * to get the spans, deadline and cadence back.
 */

private const val MAX_DAYS_WITHOUT_DATE = 6L
private val onBefore = Regex("""\bon\s+$""", RegexOption.IGNORE_CASE)
private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun String.replaceSpan(span: ChipSpan, with: String) =
    substring(0, span.range.first) + with + substring(span.range.last + 1)

private fun dayName(day: LocalDate, now: ZonedDateTime): String {
    val name = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return if (day.toEpochDay() - now.toLocalDate().toEpochDay() > MAX_DAYS_WITHOUT_DATE) "$name ${dateFormat.format(day)}" else name
}

/** Replaces the deadline [span] with "on <Weekday>", reusing an "on" already in front of it. */
fun withDeadlineDay(title: String, span: ChipSpan, day: LocalDate, now: ZonedDateTime): String {
    val hasOn = onBefore.containsMatchIn(title.substring(0, span.range.first))
    return title.replaceSpan(span, (if (hasOn) "" else "on ") + dayName(day, now))
}

/** Replaces the interval [span] with "every" (1 week) or "every N weeks". */
fun withInterval(title: String, span: ChipSpan, weeks: Int): String {
    require(weeks in 1..4) { "The interval must be 1 to 4 weeks" }
    return title.replaceSpan(span, if (weeks == 1) "every" else "every $weeks weeks")
}

/** Replaces the interval [span] with "every day" (1) or "every N days". */
fun withIntervalDays(title: String, span: ChipSpan, days: Int): String {
    require(days in 1..6) { "The interval must be 1 to 6 days" }
    return title.replaceSpan(span, if (days == 1) "every day" else "every $days days")
}

/** Replaces the weekday [span] with [to], short or full as the replaced word was. */
fun withWeekday(title: String, span: ChipSpan, to: DayOfWeek): String {
    val old = title.substring(span.range.first, span.range.last + 1)
    val full = DayOfWeek.entries.any { it.name.equals(old, ignoreCase = true) }
    var name = to.getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, Locale.ENGLISH)
    if (old == old.lowercase()) name = name.lowercase()
    return title.replaceSpan(span, name)
}

/** Turns a relative deadline like "next week" into "on <Weekday>", with the date when it is over six days away. */
fun rewriteRelativeDeadline(title: String, now: ZonedDateTime): String {
    val text = title.trim()
    val span = RulesFallback.spansOf(text, ItemType.Goal).firstOrNull { it.kind == ChipKind.Deadline } ?: return title
    val end = RulesFallback.periodEnd(text.substring(span.range.first, span.range.last + 1), now) ?: return title
    return withDeadlineDay(text, span, end.toLocalDate(), now)
}
