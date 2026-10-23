package com.juice.core.character

enum class Emotion { NEUTRAL, HAPPY, STERN, THINKING }

data class ParsedReply(val emotion: Emotion, val text: String)

object CoachReplyText {
    private val leadingTag = Regex("""^\s*\[([A-Za-z]+)]""")

    /** Reads a leading `[emotion]` tag off [raw]; any leading `[word]` is stripped, unknown ones count as [Emotion.NEUTRAL]. */
    fun parse(raw: String): ParsedReply {
        val match = leadingTag.find(raw) ?: return ParsedReply(Emotion.NEUTRAL, raw.trim())
        val emotion = Emotion.entries.firstOrNull { it.name.equals(match.groupValues[1], ignoreCase = true) } ?: Emotion.NEUTRAL
        return ParsedReply(emotion, raw.substring(match.range.last + 1).trim())
    }
}
