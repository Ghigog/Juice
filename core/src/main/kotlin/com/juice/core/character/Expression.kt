package com.juice.core.character

enum class Shape { SQUARE, CIRCLE, TRIANGLE }

/** The shapes a character's face is drawn with. */
data class Face(val eye: Shape, val mouth: Shape)

/**
 * How a character feels in a reply. The model tags every chat reply with one of these as a `[tag]`.
 * Each has its own pose in [com.juice.core.character.face.FacePoses].
 */
enum class Expression {
    NEUTRAL, HAPPY, EXCITED, WORRIED, SAD, STERN, SURPRISED, THINKING, CHEEKY;

    val tag: String get() = "[${name.lowercase()}]"

    companion object {
        private val anyTag = Regex("""\[([A-Za-z]+)]""")

        /** Older names that still read: saved chats and model drift from before the nine were merged. */
        private val aliases = mapOf("scared" to WORRIED, "angry" to STERN)

        /** Every tag, as a prompt lists them: `[neutral], [happy], …`. */
        val TAG_LIST: String = entries.joinToString(", ") { it.tag }

        /** The expression called [name] (any case, old aliases included), or null. */
        fun named(name: String?): Expression? {
            if (name == null) return null
            val key = name.trim().lowercase()
            return entries.firstOrNull { it.name.lowercase() == key } ?: aliases[key]
        }

        /**
         * Strips every known `[expression]` tag from [reply], wherever it sits, and returns the last one with the text.
         * The words either side of a removed tag are joined by one space and the ends are trimmed. With no known tag the
         * result is [NEUTRAL] and the full text; unknown bracketed words stay.
         */
        fun parse(reply: String): Pair<Expression, String> {
            val tags = anyTag.findAll(reply).mapNotNull { m -> named(m.groupValues[1])?.let { m to it } }.toList()
            if (tags.isEmpty()) return NEUTRAL to reply
            val segments = mutableListOf<String>()
            var start = 0
            for ((match, _) in tags) {
                segments += reply.substring(start, match.range.first)
                start = match.range.last + 1
            }
            segments += reply.substring(start)
            return tags.last().second to segments.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
}
