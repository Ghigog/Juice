package com.juice.core.intake

/** Local classifier for offline, API failure and on-device-only mode. */
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

    fun classify(rawText: String): ParsedItem {
        val type = if (dateOrTimePhrase.containsMatchIn(rawText)) ItemType.Goal else ItemType.Note
        return ParsedItem(type, rawText.trim().take(IntakeValidator.MAX_TITLE_LENGTH))
    }
}
