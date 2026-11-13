package com.juice.core.intake

/** A Groq API key the person pasted. Never prints in full: [toString] is the [masked] form. */
@JvmInline
value class GroqKey private constructor(val value: String) {
    /** The first four characters, `…`, then the last four; a key of eight or fewer shows a quarter of itself at each end, so never all of it. */
    val masked: String get() {
        val shown = if (value.length > 2 * VISIBLE_EDGE) VISIBLE_EDGE else value.length / 4
        return "${value.take(shown)}…${value.takeLast(shown)}"
    }

    override fun toString(): String = masked

    companion object {
        private const val VISIBLE_EDGE = 4

        /** The key from pasted [text], trimmed; null when it is blank or holds whitespace inside. */
        fun parse(text: String): GroqKey? {
            val trimmed = text.trim()
            return if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) null else GroqKey(trimmed)
        }
    }
}
