package com.juice.core.character

enum class Shape { SQUARE, CIRCLE, TRIANGLE }

/** The shapes a character's face is drawn with. */
data class Face(val eye: Shape, val mouth: Shape)

/**
 * How a character feels in a reply. The model starts every chat reply with one of these as a `[tag]`.
 * Each has its own pose in [com.juice.core.character.face.FacePoses].
 */
enum class Expression {
    NEUTRAL, HAPPY, EXCITED, WORRIED, SAD, STERN, SURPRISED, THINKING, CHEEKY;

    val tag: String get() = "[${name.lowercase()}]"

    companion object {
        private val leadingTag = Regex("""^\s*\[([A-Za-z]+)]\s*""")

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

        /** Reads a leading `[expression]` tag off [reply] and strips it; a missing or unknown tag gives [NEUTRAL] and the full text. */
        fun parse(reply: String): Pair<Expression, String> {
            val match = leadingTag.find(reply) ?: return NEUTRAL to reply
            val expression = named(match.groupValues[1]) ?: return NEUTRAL to reply
            return expression to reply.substring(match.range.last + 1)
        }
    }
}
