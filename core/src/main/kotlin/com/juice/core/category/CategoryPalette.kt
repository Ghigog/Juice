package com.juice.core.category

/** One juice colour: [accent] is the liquid, [deep] its bottom, both ARGB, as in the flavour themes. */
data class PaletteColour(val accent: Int, val deep: Int)

/** The juice colours a person can give a category, in the order new categories take them. */
object CategoryPalette {
    val colours: List<PaletteColour> = listOf(
        PaletteColour(0xFFFFB020.toInt(), 0xFFE8590C.toInt()), // citrus
        PaletteColour(0xFFF0489E.toInt(), 0xFF6B0F5A.toInt()), // berry
        PaletteColour(0xFFA78BFA.toInt(), 0xFF2A1250.toInt()), // dragonfruit
        PaletteColour(0xFFF5C542.toInt(), 0xFFB7791F.toInt()), // honey
        PaletteColour(0xFF4ADE80.toInt(), 0xFF166534.toInt()), // lime
        PaletteColour(0xFF38BDF8.toInt(), 0xFF0C4A6E.toInt()), // blueberry
        PaletteColour(0xFFFB7185.toInt(), 0xFF9F1239.toInt()), // watermelon
        PaletteColour(0xFF2DD4BF.toInt(), 0xFF115E59.toInt()), // mint
        PaletteColour(0xFFFB923C.toInt(), 0xFF9A3412.toInt()), // mango
    )

    /** The palette colour with this [accent], or null when it is not one of them. */
    fun byAccent(accent: Int): PaletteColour? = colours.firstOrNull { it.accent == accent }

    /** The first palette colour whose accent is not in [used]; when all are used, wraps by taking the first. */
    fun firstUnused(used: Collection<Int>): PaletteColour = colours.firstOrNull { it.accent !in used } ?: colours.first()
}
