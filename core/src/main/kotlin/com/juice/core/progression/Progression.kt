package com.juice.core.progression

/** Flavours unlock at a level (design doc §6.4). Highest unlocked one is active. */
enum class Flavour(val displayName: String, val unlockLevel: Int) {
    ELECTRIC_CITRUS("Electric Citrus", 1),
    BERRY_SURGE("Berry Surge", 5),
    DRAGONFRUIT_OBSIDIAN("Dragonfruit Obsidian", 10),
    GOLDEN_HONEY("Golden Honey", 25),
}

object Progression {
    /** Total Juice needed to reach level 2; level n needs JUICE_STEP * (n - 1)^2. */
    const val JUICE_STEP = 100L

    fun thresholdFor(level: Int): Long {
        require(level >= 1) { "level must be >= 1" }
        val n = (level - 1).toLong()
        return JUICE_STEP * n * n
    }

    fun levelFor(totalJuice: Long): Int {
        var level = 1
        while (totalJuice >= thresholdFor(level + 1)) level++
        return level
    }

    fun flavourFor(level: Int): Flavour = Flavour.entries.last { it.unlockLevel <= level }

    fun flavourForJuice(totalJuice: Long): Flavour = flavourFor(levelFor(totalJuice))
}
