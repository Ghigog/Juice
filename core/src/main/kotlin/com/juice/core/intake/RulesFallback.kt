package com.juice.core.intake

import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Local classifier for offline, API failure and on-device-only mode, and the final say on a card's type: the first of [typeRules] to match wins.
 * A list ("Title: a, b and c", "get a, b and c") makes a Project, a cadence phrase ("every Sunday") a Routine,
 * a date or time phrase a Goal, a leading action verb ("find my passport") a Task, anything else a Note.
 */
object RulesFallback {
    /** Spans of time; a bare "week" or "month" needs "this", "next" or "end of" to count. */
    private const val PERIOD =
        """end of (the |this |next )?(day|week|month)|eod|eow|eom|""" +
            """(this |next )(week ?end|week|month)|(the )?week ?end(?!\s+\w)"""
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
    private val verbs = listOf(
        "buy", "call", "send", "email", "finish", "do", "write", "fix", "book", "pay", "order", "reply", "clean", "plan",
        "review", "ship", "pick", "make", "record", "submit", "check", "update", "cancel", "renew", "schedule", "prepare",
        "draft", "read", "text", "ask", "return", "file", "tidy", "wash", "find", "go", "get", "take", "bring", "look",
        "set", "try", "remember", "put", "move", "change", "open", "stop", "start", "see", "meet", "visit", "grab",
        "fetch", "collect", "drop", "cook", "bake", "water", "feed", "walk", "run", "practice", "study", "learn",
        "install", "download", "upload", "sign", "apply", "register", "contact", "message", "rent", "sell", "donate",
        "repair", "replace", "build", "create", "design", "test", "deploy", "organise", "organize", "sort", "pack",
        "unpack", "load", "empty", "vacuum", "sweep", "mop", "iron", "fold", "charge", "print", "scan", "share",
        "post", "invite", "thank", "confirm", "arrange", "deliver", "throw", "hang", "water", "rinse", "wipe",
        "join", "attend", "enrol", "enroll", "subscribe", "reserve", "request", "remind", "follow", "watch", "speak",
        "talk", "discuss", "volunteer", "complete", "add", "remove", "delete",
    )
    private val leadingVerb = Regex(
        """^(?:(?:please|pls|to)\s+)*(${verbs.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE,
    )
    /** Where a shopping-style list ends and the rest of the sentence ("when I go to…", "tomorrow") begins. */
    private val listEnd = Regex(
        """\s+(?:when|while|at|from|in|on|by|before|after|for|if|once|every|daily|weekly|today|tonight|tomorrow|next|this|within)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_LIST_ITEM_WORDS = 4
    private const val MIN_PLAIN_LIST_ITEMS = 3
    private val intervalCadence = Regex(
        """\bevery\s+(?:(\d+|two|three|four)\s+weeks?|other(?:\s+week)?|second\s+week)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val clockTime = Regex("""\b(?:(?:by|at|before)\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\b(?:by|at|before)\s+(\d{1,2})(?::(\d{2}))?\b""", RegexOption.IGNORE_CASE)
    private val dayWord = Regex("""\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)
    private const val WEEKDAY = """(?:mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:r(?:s(?:day)?)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b"""
    private val dailyPhrase = Regex("""\b(?:every ?day|daily)\b""", RegexOption.IGNORE_CASE)
    private val weekdayCadence = Regex(
        """\b(?:every|weekly on)\s+($WEEKDAY(?:\s*(?:,|/|&|\band\b)\s*$WEEKDAY)*)""",
        RegexOption.IGNORE_CASE,
    )
    private val weekdayAnywhere = Regex("""\b$WEEKDAY""", RegexOption.IGNORE_CASE)
    private val weekdayName = Regex(WEEKDAY, RegexOption.IGNORE_CASE)
    private val listSplit = Regex(""",|\band\b""", RegexOption.IGNORE_CASE)
    private val period = Regex("""\b($PERIOD)\b""", RegexOption.IGNORE_CASE)
    private val within = Regex("""\b$WITHIN\b""", RegexOption.IGNORE_CASE)

    private class Input(val text: String, val title: String, val now: ZonedDateTime?)

    /** The type rules, checked in order; the first to answer decides. To add a rule, add an entry here and its test. */
    private val typeRules: List<(Input) -> ParsedItem?> = listOf(
        ::projectRule, // several concrete tasks in a list
        ::routineRule, // "every …" or another repeat
        ::goalRule, // a date or time
        ::taskRule, // starts with a verb
        { ParsedItem(ItemType.Note, it.title) }, // anything else, "I'd like to…" and "one day…" included
    )

    /** [now] resolves simple phrases like "by 6pm" or "tomorrow" into a deadline. */
    fun classify(rawText: String, now: ZonedDateTime? = null): ParsedItem {
        val text = rawText.trim()
        val input = Input(text, text.take(IntakeValidator.MAX_TITLE_LENGTH), now)
        return typeRules.firstNotNullOf { it(input) }
    }

    /** The words of [rawText] that made it a [type], as ranges of its title. Notes and Projects have none. */
    fun spansOf(rawText: String, type: ItemType): List<ChipSpan> {
        val text = rawText.trim()
        val spans = when (type) {
            ItemType.Task -> listOfNotNull(leadingVerb.find(text)?.groups?.get(1)?.let { ChipSpan(ChipKind.Verb, it.range) })
            ItemType.Goal -> listOfNotNull(
                (dayWord.find(text) ?: period.find(text) ?: within.find(text))?.let { ChipSpan(ChipKind.Deadline, it.range) },
            )
            ItemType.Routine -> routineSpans(text)
            ItemType.Note, ItemType.Project, ItemType.List -> emptyList()
        }
        return spans.filter { it.range.last < IntakeValidator.MAX_TITLE_LENGTH }
    }

    private fun routineSpans(text: String): List<ChipSpan> {
        intervalCadence.find(text)?.let { m ->
            return listOf(ChipSpan(ChipKind.Interval, m.range)) + weekdaySpans(weekdayAnywhere.findAll(text, m.range.last))
        }
        dailyPhrase.find(text)?.let { return listOf(ChipSpan(ChipKind.Interval, it.range)) }
        val m = weekdayCadence.find(text) ?: return emptyList()
        val list = m.groups[1]!!
        val prefix = text.substring(m.range.first, list.range.first).trimEnd()
        val interval = ChipSpan(ChipKind.Interval, m.range.first until m.range.first + prefix.length)
        // Offset the matches inside the list group back into the title.
        val days = weekdayName.findAll(list.value).map {
            ChipSpan(ChipKind.Weekday, (it.range.first + list.range.first)..(it.range.last + list.range.first))
        }
        return listOf(interval) + days
    }

    private fun weekdaySpans(matches: Sequence<MatchResult>) = matches.map { ChipSpan(ChipKind.Weekday, it.range) }.toList()

    private fun projectRule(i: Input): ParsedItem? {
        val (title, steps) = colonList(i.text) ?: plainList(i.text) ?: return null
        return ParsedItem(
            ItemType.Project,
            title?.take(IntakeValidator.MAX_TITLE_LENGTH) ?: i.title,
            deadline = deadlineOf(i),
            subItems = steps.take(IntakeValidator.MAX_SUB_ITEMS),
        )
    }

    private fun routineRule(i: Input): ParsedItem? =
        cadenceOf(i.text)?.let { ParsedItem(ItemType.Routine, i.title, cadence = it) }

    private fun goalRule(i: Input): ParsedItem? =
        if (dateOrTimePhrase.containsMatchIn(i.text)) ParsedItem(ItemType.Goal, i.title, deadline = deadlineOf(i)) else null

    private fun taskRule(i: Input): ParsedItem? =
        if (leadingVerb.containsMatchIn(i.text)) ParsedItem(ItemType.Task, i.title) else null

    private fun deadlineOf(i: Input) = i.now?.let { deadlineIn(i.text, it) }?.toInstant()

    /** "every day", "daily", "every Mon and Thu" or "weekly on Friday", with an optional clock time. */
    private fun cadenceOf(text: String): Cadence? {
        intervalCadence.find(text)?.let { m ->
            val days = weekdayAnywhere.findAll(text, m.range.last).map { dayOfWeek(it.value) }.toSet()
            if (days.isEmpty()) return null
            val interval = when (val n = m.groupValues[1].lowercase()) {
                "" -> 2
                "two" -> 2
                "three" -> 3
                "four" -> 4
                else -> n.toInt()
            }
            return if (interval >= 1) Cadence(days, clockTimeIn(text), interval) else null
        }
        val days = if (dailyPhrase.containsMatchIn(text)) {
            DayOfWeek.entries.toSet()
        } else {
            val list = weekdayCadence.find(text)?.groupValues?.get(1) ?: return null
            weekdayName.findAll(list).map { dayOfWeek(it.value) }.toSet()
        }
        return Cadence(days, clockTimeIn(text))
    }

    private fun dayOfWeek(name: String): DayOfWeek =
        DayOfWeek.entries.first { it.name.startsWith(name.take(3).uppercase()) }

    private fun clockTimeIn(text: String): LocalTime? {
        val g = clockTime.find(text)?.groupValues ?: return null
        var hour = (g[1].ifEmpty { g[4] }).toInt()
        val minute = (g[2].ifEmpty { g[5] }).ifEmpty { "0" }.toInt()
        when (g[3].lowercase()) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    /** "Launch: write copy, record demo and ship" is a Project titled "Launch" with three steps. */
    private fun colonList(text: String): Pair<String?, List<String>>? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val steps = text.substring(colon + 1).split(listSplit).map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.size < 2) return null
        return text.substring(0, colon).trim() to steps.map { s -> s.replaceFirstChar { it.uppercase() } }
    }

    /**
     * A list with no colon: "get tape, rope and boxes at the warehouse" gives "Get tape", "Get rope", "Get boxes";
     * "call mum, email Sam and book flights" keeps each step's own verb.
     */
    private fun plainList(text: String): Pair<String?, List<String>>? {
        val verb = leadingVerb.find(text) ?: return null
        val rest = text.substring(verb.range.last + 1).replace(listEnd, "").trimEnd('.', '!', ' ')
        val items = rest.split(listSplit).map { it.trim() }.filter { it.isNotEmpty() }
        if (items.any { it.split(Regex("""\s+""")).size > MAX_LIST_ITEM_WORDS }) return null
        val head = verb.groupValues[1].lowercase()
        // Each item after the first leading with its own verb: the list is already steps.
        if (items.size >= 2 && items.drop(1).all { leadingVerb.containsMatchIn(it) }) {
            return null to (listOf("$head ${items.first()}") + items.drop(1)).map { s -> s.replaceFirstChar { it.uppercase() } }
        }
        if (items.size < MIN_PLAIN_LIST_ITEMS) return null
        return null to items.map { "$head $it".replaceFirstChar { c -> c.uppercase() } }
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
    internal fun periodEnd(text: String, now: ZonedDateTime): ZonedDateTime? {
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
