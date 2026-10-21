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
    private val monthNames = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private val dateOrTimePhrase = Regex(
        """\b(today|tonight|tomorrow|next (week|month|year|mon|tue|wed|thu|fri|sat|sun)\w*|""" +
            """(mon|tues?|wed(nes)?|thu(rs)?|fri|sat(ur)?|sun)(day)?|""" +
            """($monthNames)[a-z]*\.? \d{1,2}|\d{1,2}(st|nd|rd|th)? ($monthNames)[a-z]*|""" +
            """\d{1,2}[/-]\d{1,2}([/-]\d{2,4})?|\d{4}-\d{2}-\d{2}|""" +
            """\d{1,2}(:\d{2})? ?(am|pm)|\d{1,2}:\d{2}|noon|midnight|""" +
            """in \d+ (minutes?|hours?|days?|weeks?|months?)|(by|before|at) \d{1,2})\b""",
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
        val time = clockTime.find(text)
        val day = dayWord.find(text)?.value?.lowercase()
        if (time == null && day == null) return null
        var hour = 9
        var minute = 0
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
        } else if (day == "tonight") {
            hour = 20
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
}
