package com.juice.core.intake

/** A Groq API key the person pasted. Never prints in full: [toString] is the [masked] form. */
@JvmInline
value class GroqKey private constructor(val value: String) {
    /** `gsk_…` plus the last four characters. */
    val masked: String get() = "gsk_…${value.takeLast(VISIBLE_TAIL)}"

    override fun toString(): String = masked

    companion object {
        private const val VISIBLE_TAIL = 4

        /** The key from pasted [text], trimmed; null when it is blank or holds whitespace inside. */
        fun parse(text: String): GroqKey? {
            val trimmed = text.trim()
            return if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) null else GroqKey(trimmed)
        }
    }
}
