package com.juice.core.intake

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Local classifier for offline, API failure and on-device-only mode: a date or time phrase makes a
 * Goal, "Title: a, b and c" makes a Project, an action verb makes a Task, anything else is a Note.
 */
object RulesFallback {
    /** Spans of time; a bare "week" or "month" needs "this", "next" or "end of" to count. */
    private const val PERIOD =
        """end of (the |this |next )?(day|week|month)|eod|eow|eom|""" +
            """(this |next )(week ?end|week|month)|(the )?week ?end"""
    private const val WITHIN = """(?:in|within) (\d+|a|an|one) (hours?|days?|weeks?|months?)"""

    private val monthNames = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private val dateOrTimePhrase = Regex(
        """\b(today|tonight|tomorrow|next (week|month|year|mon|tue|wed|thu|fri|sat|sun)\w*|""" +
            """(mon|tues?|wed(nes)?|thu(rs)?|fri|sat(ur)?|sun)(day)?|""" +
            """($monthNames)[a-z]*\.? \d{1,2}|\d{1,2}(st|nd|rd|th)? ($monthNames)[a-z]*|""" +
            """\d{1,2}[/-]\d{1,2}([/-]\d{2,4})?|\d{4}-\d{2}-\d{2}|""" +
            """\d{1,2}(:\d{2})? ?(am|pm)|\d{1,2}:\d{2}|noon|midnight|""" +
            """in \d+ (minutes?|hours?|days?|weeks?|months?)|(by|before|at) \d{1,2}|""" +
            """$PERIOD|$WITHIN)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val actionVerb = Regex(
        """\b(buy|call|send|email|finish|do|write|fix|book|pay|order|reply|clean|plan|review|ship|pick|make|record|""" +
            """submit|check|update|cancel|renew|schedule|prepare|draft|read|text|ask|return|file|tidy|wash)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val clockTime = Regex("""\b(?:(?:by|at|before)\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\b(?:by|at|before)\s+(\d{1,2})(?::(\d{2}))?\b""", RegexOption.IGNORE_CASE)
    private val dayWord = Regex("""\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)
    private val listSplit = Regex(""",|\band\b""", RegexOption.IGNORE_CASE)
    private val period = Regex("""\b($PERIOD)\b""", RegexOption.IGNORE_CASE)
    private val within = Regex("""\b$WITHIN\b""", RegexOption.IGNORE_CASE)

    /** [now] resolves simple phrases like "by 6pm" or "tomorrow" into a deadline. */
    fun classify(rawText: String, now: ZonedDateTime? = null): ParsedItem {
        val text = rawText.trim()
        val title = text.take(IntakeValidator.MAX_TITLE_LENGTH)
        projectOf(text)?.let { return it }
        if (dateOrTimePhrase.containsMatchIn(text)) {
            return ParsedItem(ItemType.Goal, title, deadline = now?.let { deadlineIn(text, it) }?.toInstant())
        }
        val type = if (actionVerb.containsMatchIn(text)) ItemType.Task else ItemType.Note
        return ParsedItem(type, title)
    }

    /** "Launch: write copy, record demo and ship" is a Project with three steps. */
    private fun projectOf(text: String): ParsedItem? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val steps = text.substring(colon + 1).split(listSplit).map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.size < 2) return null
        return ParsedItem(
            ItemType.Project,
            text.substring(0, colon).trim().take(IntakeValidator.MAX_TITLE_LENGTH),
            subItems = steps.take(IntakeValidator.MAX_SUB_ITEMS).map { s -> s.replaceFirstChar { it.uppercase() } },
        )
    }

    /** The day and clock time named in [text], or null when it names neither. Always in the future. */
    internal fun deadlineIn(text: String, now: ZonedDateTime): ZonedDateTime? {
        periodEnd(text, now)?.let { return it }
        val time = clockTime.find(text)
        val day = dayWord.find(text)?.value?.lowercase()
        if (time == null && day == null) return null
        // A day with no time means by the end of that day.
        var hour = END_OF_DAY_HOUR
        var minute = END_OF_DAY_MINUTE
        if (time != null) {
            val g = time.groupValues
            hour = (g[1].ifEmpty { g[4] }).toInt()
            minute = (g[2].ifEmpty { g[5] }).ifEmpty { "0" }.toInt()
            when (g[3].lowercase()) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
                // "by 6" with no am/pm means the next 6 o'clock on the clock face.
                else -> if (hour in 1..11 && hour < now.hour && hour + 12 > now.hour) hour += 12
            }
            if (hour !in 0..23 || minute !in 0..59) return null
        }
        var date = now.toLocalDate()
        when (day) {
            null, "today", "tonight" -> Unit
            "tomorrow" -> date = date.plusDays(1)
            else -> date = date.with(TemporalAdjusters.next(DayOfWeek.valueOf(day.uppercase())))
        }
        var at = date.atTime(hour, minute).atZone(now.zone)
        if (!at.isAfter(now)) {
            if (day != null && day != "today" && day != "tonight") return null
            at = at.plusDays(1)
        }
        return at.truncatedTo(ChronoUnit.MINUTES)
    }

    /**
     * Spans rather than moments: "end of the week", "this weekend", "next month" mean the end of
     * that span; "in 3 days" or "within a week" count from now.
     */
    private fun periodEnd(text: String, now: ZonedDateTime): ZonedDateTime? {
        within.find(text)?.let { m ->
            val n = m.groupValues[1].lowercase().let { if (it in setOf("a", "an", "one")) 1L else it.toLong() }
            val unit = when (m.groupValues[2].lowercase().removeSuffix("s")) {
                "hour" -> ChronoUnit.HOURS
                "day" -> ChronoUnit.DAYS
                "week" -> ChronoUnit.WEEKS
                else -> ChronoUnit.MONTHS
            }
            return now.plus(n, unit).truncatedTo(ChronoUnit.MINUTES)
        }
        val span = period.find(text)?.value?.lowercase()?.replace(Regex("\\s+"), " ")?.replace("week end", "weekend")
            ?: return null
        val today = now.toLocalDate()
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val date = when {
            span.endsWith("day") || span == "eod" -> today
            span.startsWith("next") && (span.endsWith("week") || span.endsWith("weekend")) -> sunday.plusWeeks(1)
            span.startsWith("next") && span.endsWith("month") -> today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
            span.endsWith("week") || span.endsWith("weekend") || span == "eow" -> sunday
            span.endsWith("month") || span == "eom" -> today.with(TemporalAdjusters.lastDayOfMonth())
            else -> return null
        }
        return date.atTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE).atZone(now.zone)
    }

    private const val END_OF_DAY_HOUR = 23
    private const val END_OF_DAY_MINUTE = 59
}
