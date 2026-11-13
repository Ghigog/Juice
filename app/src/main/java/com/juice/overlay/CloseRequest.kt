package com.juice.overlay

import androidx.compose.runtime.mutableStateOf

/**
 * The overlay's way out. Every close (background tap, BACK, any other) goes through [request]; the overlay animates
 * while [closing] is true and calls [closed] when done, and only then is [finish] (remove overlay, show handle) run.
 */
class CloseRequest(private val reduceMotion: () -> Boolean, private val finish: () -> Unit) {
    val closing = mutableStateOf(false)

    /** Starts closing; ignored while already closing. With reduced motion it finishes at once. */
    fun request() {
        if (closing.value) return
        if (reduceMotion()) finish() else closing.value = true
    }

    /** The close animation has ended. Ignored unless a close is under way, so a torn-down overlay never shows the handle twice. */
    fun closed() {
        if (!closing.value) return
        closing.value = false
        finish()
    }

    /** The overlay is gone or newly built; forget any close under way. */
    fun reset() {
        closing.value = false
    }
}
