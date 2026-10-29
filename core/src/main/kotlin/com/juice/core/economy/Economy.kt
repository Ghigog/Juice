package com.juice.core.economy

/** Item size classes, with base Juice and Thirst Meter fill (percent) per completion. */
enum class ItemType { NOTE, TASK, LIST, GOAL, PROJECT, ROUTINE }

/** Tunable economy values (design doc §6). Meter values are percentages, 0..100. */
data class EconomyConfig(
    val baseXp: Map<ItemType, Long> = mapOf(
        ItemType.NOTE to 10L, ItemType.TASK to 25L, ItemType.LIST to 75L, ItemType.GOAL to 100L, ItemType.PROJECT to 500L,
        ItemType.ROUTINE to 25L,
    ),
    val fillPercent: Map<ItemType, Double> = mapOf(
        ItemType.NOTE to 5.0, ItemType.TASK to 12.0, ItemType.LIST to 25.0, ItemType.GOAL to 30.0, ItemType.PROJECT to 50.0,
        ItemType.ROUTINE to 12.0,
    ),
    /** Meter percent lost per minute of drain; the default is 1% per hour. */
    val drainPercentPerMinute: Double = 1.0 / 60,
    /** Meter at or below this is Thirsty (1×). */
    val thirstyMax: Double = 25.0,
    /** Meter above this is Satiated. Hydrated spans (thirstyMax, satiatedAbove]. */
    val satiatedAbove: Double = 75.0,
    val thirstyMultiplier: Double = 1.0,
    val hydratedMin: Double = 2.0,
    val hydratedMax: Double = 4.0,
    val satiatedMultiplier: Double = 8.0,
)

data class EconomyState(val juice: Long = 0, val meter: Double = 0.0)

/** A completion that can be undone; dismissing an item never produces one. */
data class Completion(val earned: Long, val appliedFill: Double)

class Economy(private val config: EconomyConfig = EconomyConfig()) {

    fun multiplier(meter: Double): Double = when {
        meter <= config.thirstyMax -> config.thirstyMultiplier
        meter > config.satiatedAbove -> config.satiatedMultiplier
        else -> {
            val start = config.thirstyMax + 1 // 26% is the first Hydrated point
            val t = (meter - start) / (config.satiatedAbove - start)
            config.hydratedMin + t * (config.hydratedMax - config.hydratedMin)
        }
    }

    /** Multiplier is read from the meter before this completion's fill is applied. */
    fun complete(state: EconomyState, type: ItemType): Pair<EconomyState, Completion> {
        val earned = Math.round(config.baseXp.getValue(type) * multiplier(state.meter))
        val meter = (state.meter + config.fillPercent.getValue(type)).coerceAtMost(100.0)
        return EconomyState(state.juice + earned, meter) to Completion(earned, meter - state.meter)
    }

    fun undo(state: EconomyState, completion: Completion): EconomyState =
        EconomyState(
            (state.juice - completion.earned).coerceAtLeast(0),
            (state.meter - completion.appliedFill).coerceAtLeast(0.0),
        )

    /** Dismissing an item gives no Juice and no fill. */
    fun dismiss(state: EconomyState): EconomyState = state

    fun drain(state: EconomyState, minutes: Double): EconomyState =
        state.copy(meter = (state.meter - config.drainPercentPerMinute * minutes).coerceAtLeast(0.0))
}
