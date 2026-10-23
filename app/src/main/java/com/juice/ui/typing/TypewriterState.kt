package com.juice.ui.typing

/** How much of [text] a letter-by-letter reveal has shown so far. Pure: the caller supplies the timing. */
class TypewriterState(val text: String, val delayMs: Long = DEFAULT_DELAY_MS) {
    var revealed = 0
        private set

    val done: Boolean get() = revealed >= text.length

    val visible: String get() = text.substring(0, revealed)

    /** Reveals one more character. Returns it, or null when everything is already shown. */
    fun advance(): Char? = if (done) null else text[revealed++]

    /** Shows the whole text at once. */
    fun skip() {
        revealed = text.length
    }

    companion object {
        const val DEFAULT_DELAY_MS = 30L
    }
}
