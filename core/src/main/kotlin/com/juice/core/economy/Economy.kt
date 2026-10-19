package com.juice.core.economy

import kotlin.math.roundToLong

/** Player's Juice total and Thirst Meter (percent, 0 to 100). */
data class EconomyState(val juice: Long = 0, val meter: Double = 0.0)

/** Result of a completion; keep it to [Economy.undo] the completion. */
data class Completion(
    val type: ItemType,
    val multiplier: Double,
    val juiceEarned: Long,
    val before: EconomyState,
    val after: EconomyState,
)

class Economy(private val config: EconomyConfig = EconomyConfig()) {

    /** Earns Juice at the multiplier of the meter as it is now, then adds the type's fill. */
    fun complete(state: EconomyState, type: ItemType): Completion {
        val value = config.valueOf(type)
        val multiplier = config.multiplierAt(state.meter)
        val earned = (value.baseXp * multiplier).roundToLong()
        val after = EconomyState(
            juice = state.juice + earned,
            meter = (state.meter + value.fillPercent).coerceAtMost(MAX_METER),
        )
        return Completion(type, multiplier, earned, before = state, after = after)
    }

    /** Fully reverts a completion: Juice and meter return to their values before it. */
    fun undo(completion: Completion): EconomyState = completion.before

    /** Dismissing an item gives no Juice and no fill. */
    fun dismiss(state: EconomyState): EconomyState = state

    /** Drains the meter for [minutes] of elapsed time. */
    fun drain(state: EconomyState, minutes: Double): EconomyState =
        state.copy(meter = (state.meter - config.drainPercentPerMinute * minutes).coerceAtLeast(0.0))

    private companion object {
        const val MAX_METER = 100.0
    }
}
