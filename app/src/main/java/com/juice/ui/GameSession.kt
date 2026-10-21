package com.juice.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Thirst Meter bands (design doc §6.2). */
enum class Tier { THIRSTY, HYDRATED, SATIATED }

/**
 * The game loop behind the overlay: owns Juice and the Thirst Meter, persists them, and plays
 * feedback. [onSatiated] fires when a completion pushes the meter into Satiated (for particles);
 * [onLevelUp] gets the new level and the flavour it unlocked, if any. [chosenFlavour] is the
 * player's pick; it only applies once unlocked, otherwise the highest unlocked flavour is used.
 * [state] is snapshot state, so a composable reading it redraws when it changes.
 */
class GameSession(
    private val store: EconomyStore,
    private val feedback: Feedback,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: EconomyConfig = EconomyConfig(),
    private val onSatiated: () -> Unit = {},
    private val onFlavourChanged: (FlavourTheme) -> Unit = {},
    private val onLevelUp: (level: Int, unlocked: Flavour?) -> Unit = { _, _ -> },
    private val chosenFlavour: () -> Flavour? = { null },
) {
    private val economy = Economy(config)
    private val undoStack = ArrayDeque<Completion>()
    private var lastTick = 0L

    var state by mutableStateOf(EconomyState())
        private set

    val multiplier: Double get() = economy.multiplier(state.meter)

    val tier: Tier get() = when {
        state.meter <= config.thirstyMax -> Tier.THIRSTY
        state.meter > config.satiatedAbove -> Tier.SATIATED
        else -> Tier.HYDRATED
    }

    val level: Int get() = Progression.levelFor(state.juice)

    /** How far through the current level, 0..1. */
    val levelProgress: Float get() {
        val lo = Progression.thresholdFor(level)
        val hi = Progression.thresholdFor(level + 1)
        return ((state.juice - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }

    val flavour: Flavour get() =
        chosenFlavour()?.takeIf { it.unlockLevel <= level } ?: Progression.flavourForJuice(state.juice)

    val theme: FlavourTheme get() = FlavourTheme.of(flavour)

    /** Meter as the 0..1 reservoir fill. */
    val fill: Float get() = (state.meter / 100.0).toFloat()

    /** Juice that completing an item of [type] would earn right now. */
    fun worth(type: ItemType): Long = Math.round(config.baseXp.getValue(type) * multiplier)

    fun baseXp(type: ItemType): Long = config.baseXp.getValue(type)

    /** Loads the saved state and drains the meter for the time the overlay was closed. */
    fun open() {
        val saved = store.load()
        val now = clock()
        lastTick = now
        state = saved?.let {
            val minutes = ((now - it.savedAtMillis).coerceAtLeast(0)) / 60_000.0
            economy.drain(it.state, minutes)
        } ?: EconomyState()
        undoStack.clear()
        persist()
        applyFlavour()
    }

    /** Drains the meter for the time since the last tick, while the overlay stays open. */
    fun tick() {
        val now = clock()
        val minutes = (now - lastTick).coerceAtLeast(0) / 60_000.0
        lastTick = now
        if (state.meter <= 0.0 || minutes <= 0.0) return
        state = economy.drain(state, minutes)
        persist()
    }

    /** Completes an item of [type]; returns the Juice it earned. */
    fun complete(type: ItemType): Long {
        val wasSatiated = state.meter > config.satiatedAbove
        val levelBefore = level
        val (next, completion) = economy.complete(state, type)
        state = next
        undoStack.addLast(completion)
        persist()
        feedback.play(FeedbackEvent.CHECKMARK, boosted = wasSatiated)
        if (!wasSatiated && state.meter > config.satiatedAbove) {
            feedback.play(FeedbackEvent.SATIATED_PEAK)
            onSatiated()
        }
        if (level > levelBefore) {
            feedback.play(FeedbackEvent.LEVEL_UP)
            onLevelUp(level, Flavour.entries.lastOrNull { it.unlockLevel in (levelBefore + 1)..level })
        }
        applyFlavour()
        return completion.earned
    }

    /** Reverts the latest completion's Juice and fill. */
    fun undo() {
        val completion = undoStack.removeLastOrNull() ?: return
        state = economy.undo(state, completion)
        persist()
        applyFlavour()
    }

    private var appliedFlavour: Flavour? = null

    /** Switches the sounds and tells the UI when the flavour to use differs from the one applied. */
    fun applyFlavour() {
        if (flavour == appliedFlavour) return
        appliedFlavour = flavour
        feedback.useSoundSet(theme.soundSet)
        onFlavourChanged(theme)
    }

    private fun persist() = store.save(state, clock())
}
