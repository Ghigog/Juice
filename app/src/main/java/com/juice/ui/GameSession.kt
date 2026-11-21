package com.juice.ui

import java.time.Clock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.core.economy.CategoryEconomy
import com.juice.core.economy.CategoryCompletion
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
 * The game loop behind the overlay: owns every category's Juice and Thirst Meter, persists them, and plays
 * feedback. [onSatiated] fires when a completion pushes the meter into Satiated (for particles);
 * [onLevelUp] gets the new level and the flavour it unlocked, if any. [chosenFlavour] is the
 * player's pick; it only applies once unlocked, otherwise the highest unlocked flavour is used.
 * [state] is snapshot state, so a composable reading it redraws when it changes.
 */
class GameSession(
    private val store: EconomyStore,
    private val feedback: Feedback,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val config: EconomyConfig = EconomyConfig(),
    private val onSatiated: () -> Unit = {},
    private val onFlavourChanged: (FlavourTheme) -> Unit = {},
    private val onLevelUp: (level: Int, unlocked: Flavour?) -> Unit = { _, _ -> },
    private val chosenFlavour: () -> Flavour? = { null },
) {
    private val undoStack = ArrayDeque<CategoryCompletion>()
    private var lastTick = 0L

    /** Every category's Juice and Thirst Meter; snapshot state, so the HUD follows it. */
    private var all by mutableStateOf(CategoryEconomy())

    /** The Juice and meter of [categoryId]; an untouched category is empty. */
    fun state(categoryId: String): EconomyState = all[categoryId]

    fun multiplier(categoryId: String): Double = all.multiplier(categoryId)

    fun tier(categoryId: String): Tier = tierOf(state(categoryId).meter)

    /** Total Juice across every category: what level and flavour unlocks read. */
    val totalJuice: Long get() = all.totalJuice

    val level: Int get() = Progression.levelFor(totalJuice)

    /** How far through the current level, 0..1. */
    val levelProgress: Float get() {
        val lo = Progression.thresholdFor(level)
        val hi = Progression.thresholdFor(level + 1)
        return ((totalJuice - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }

    val flavour: Flavour get() =
        chosenFlavour()?.takeIf { it.unlockLevel <= level } ?: Progression.flavourForJuice(totalJuice)

    val theme: FlavourTheme get() = FlavourTheme.of(flavour)

    /** [categoryId]'s meter as the 0..1 reservoir fill. */
    fun fill(categoryId: String): Float = (state(categoryId).meter / 100.0).toFloat()

    /** Juice that completing an item of [type] in [categoryId] would earn right now. */
    fun worth(type: ItemType, categoryId: String): Long = Math.round(config.baseXp.getValue(type) * multiplier(categoryId))

    fun baseXp(type: ItemType): Long = config.baseXp.getValue(type)

    private fun tierOf(meter: Double) = when {
        meter <= config.thirstyMax -> Tier.THIRSTY
        meter > config.satiatedAbove -> Tier.SATIATED
        else -> Tier.HYDRATED
    }

    /** Loads the saved state and drains every category for the time the overlay was closed. */
    fun open() {
        val loaded = store.load()
        val now = clock.millis()
        lastTick = now
        all = loaded?.let {
            val minutes = ((now - it.savedAtMillis).coerceAtLeast(0)) / 60_000.0
            it.economy.drain(minutes)
        } ?: CategoryEconomy()
        undoStack.clear()
        persist()
        applyFlavour()
    }

    /** Drains every category for the time since the last tick, while the overlay stays open. */
    fun tick() {
        val now = clock.millis()
        val minutes = (now - lastTick).coerceAtLeast(0) / 60_000.0
        lastTick = now
        if (minutes <= 0.0 || all.states.values.none { it.meter > 0.0 }) return
        all = all.drain(minutes)
        persist()
    }

    /** Completes an item of [type] in [categoryId]; returns the Juice it earned. */
    fun complete(type: ItemType, categoryId: String): Long {
        val wasSatiated = tier(categoryId) == Tier.SATIATED
        val levelBefore = level
        val multiplierBefore = multiplier(categoryId)
        val (next, completion) = all.complete(categoryId, type)
        all = next
        undoStack.addLast(completion)
        persist()
        feedback.playCompletion(multiplierBefore)
        if (!wasSatiated && tier(categoryId) == Tier.SATIATED) {
            feedback.play(FeedbackEvent.SATIATED_PEAK)
            onSatiated()
        }
        if (level > levelBefore) {
            feedback.play(FeedbackEvent.LEVEL_UP)
            onLevelUp(level, Flavour.entries.lastOrNull { it.unlockLevel in (levelBefore + 1)..level })
        }
        applyFlavour()
        return completion.completion.earned
    }

    /** Reverts the latest completion's Juice and fill, on the category that earned it. */
    fun undo() {
        val completion = undoStack.removeLastOrNull() ?: return
        all = all.undo(completion)
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

    private fun persist() = store.save(all, clock.millis())
}
