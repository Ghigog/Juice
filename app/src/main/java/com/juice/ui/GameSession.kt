package com.juice.ui

import com.juice.core.economy.Completion
import com.juice.core.economy.Economy
import com.juice.core.economy.EconomyConfig
import com.juice.core.economy.EconomyState
import com.juice.core.economy.ItemType
import com.juice.data.EconomyStore
import com.juice.feedback.Feedback
import com.juice.feedback.FeedbackEvent

/**
 * The game loop behind the overlay: owns Juice and the Thirst Meter, persists them, and plays
 * feedback. [onSatiated] fires when a completion pushes the meter into Satiated (for particles).
 */
class GameSession(
    private val store: EconomyStore,
    private val feedback: Feedback,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: EconomyConfig = EconomyConfig(),
    private val onSatiated: () -> Unit = {},
) {
    private val economy = Economy(config)
    private val undoStack = ArrayDeque<Completion>()

    var state = EconomyState()
        private set

    val multiplier: Double get() = economy.multiplier(state.meter)

    /** Meter as the 0..1 reservoir fill. */
    val fill: Float get() = (state.meter / 100.0).toFloat()

    /** Loads the saved state and drains the meter for the time the overlay was closed. */
    fun open() {
        val saved = store.load()
        val now = clock()
        state = saved?.let {
            val minutes = ((now - it.savedAtMillis).coerceAtLeast(0)) / 60_000.0
            economy.drain(it.state, minutes)
        } ?: EconomyState()
        undoStack.clear()
        persist()
    }

    fun complete(type: ItemType) {
        val wasSatiated = state.meter > config.satiatedAbove
        val (next, completion) = economy.complete(state, type)
        state = next
        undoStack.addLast(completion)
        persist()
        feedback.play(FeedbackEvent.CHECKMARK)
        if (!wasSatiated && state.meter > config.satiatedAbove) {
            feedback.play(FeedbackEvent.SATIATED_PEAK)
            onSatiated()
        }
    }

    /** Reverts the latest completion's Juice and fill. */
    fun undo() {
        val completion = undoStack.removeLastOrNull() ?: return
        state = economy.undo(state, completion)
        persist()
    }

    private fun persist() = store.save(state, clock())
}
