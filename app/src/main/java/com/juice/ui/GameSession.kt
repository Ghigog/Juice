package com.juice.ui

import com.juice.core.economy.Completion
import com.juice.core.economy.Economy
import com.juice.core.economy.EconomyConfig
import com.juice.core.economy.EconomyState
import com.juice.core.economy.ItemType
import com.juice.data.EconomyStore
import com.juice.feedback.Feedback
import com.juice.core.progression.Flavour
import com.juice.core.progression.Progression
import com.juice.feedback.FeedbackEvent
import com.juice.ui.theme.FlavourTheme

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
    private val onFlavourChanged: (FlavourTheme) -> Unit = {},
) {
    private val economy = Economy(config)
    private val undoStack = ArrayDeque<Completion>()

    var state = EconomyState()
        private set

    val multiplier: Double get() = economy.multiplier(state.meter)

    val level: Int get() = Progression.levelFor(state.juice)

    val flavour: Flavour get() = Progression.flavourForJuice(state.juice)

    val theme: FlavourTheme get() = FlavourTheme.of(flavour)

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
        applyFlavour()
    }

    fun complete(type: ItemType) {
        val wasSatiated = state.meter > config.satiatedAbove
        val (next, completion) = economy.complete(state, type)
        state = next
        undoStack.addLast(completion)
        persist()
        applyFlavour()
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
        applyFlavour()
    }

    private var appliedFlavour: Flavour? = null

    /** Switches the sounds and tells the UI when the player's flavour differs from the one applied. */
    private fun applyFlavour() {
        if (flavour == appliedFlavour) return
        appliedFlavour = flavour
        feedback.useSoundSet(theme.soundSet)
        onFlavourChanged(theme)
    }

    private fun persist() = store.save(state, clock())
}
