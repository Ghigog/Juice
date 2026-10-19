package com.juice.core.economy

/** Item types that can be completed, named as in the design doc (§6.1). */
enum class ItemType { NOTE, TASK, GOAL, PROJECT }

/** Base XP and Thirst Meter fill (in percent points) earned by completing one item type. */
data class ItemValue(val baseXp: Long, val fillPercent: Double)

/**
 * All economy tunables (design doc §6.1 and §6.2). Meter values are percentages from 0 to 100.
 *
 * Thirsty is up to [thirstyMax] (multiplier [thirstyMultiplier]); Satiated is above [hydratedMax]
 * (multiplier [satiatedMultiplier]); in between the multiplier scales linearly from
 * [hydratedMinMultiplier] at [thirstyMax] to [hydratedMaxMultiplier] at [hydratedMax].
 */
data class EconomyConfig(
    val values: Map<ItemType, ItemValue> = mapOf(
        ItemType.NOTE to ItemValue(baseXp = 10, fillPercent = 5.0),
        ItemType.TASK to ItemValue(baseXp = 25, fillPercent = 12.0),
        ItemType.GOAL to ItemValue(baseXp = 100, fillPercent = 30.0),
        ItemType.PROJECT to ItemValue(baseXp = 500, fillPercent = 50.0),
    ),
    val drainPercentPerMinute: Double = 1.0,
    val thirstyMax: Double = 25.0,
    val hydratedMax: Double = 75.0,
    val thirstyMultiplier: Double = 1.0,
    val hydratedMinMultiplier: Double = 2.0,
    val hydratedMaxMultiplier: Double = 4.0,
    val satiatedMultiplier: Double = 8.0,
) {
    fun valueOf(type: ItemType): ItemValue =
        values[type] ?: error("No economy value configured for $type")

    /** Thirst multiplier M for the meter level, 0 to 100. */
    fun multiplierAt(meter: Double): Double = when {
        meter <= thirstyMax -> thirstyMultiplier
        meter > hydratedMax -> satiatedMultiplier
        else -> hydratedMinMultiplier +
            (hydratedMaxMultiplier - hydratedMinMultiplier) * (meter - thirstyMax) / (hydratedMax - thirstyMax)
    }
}
