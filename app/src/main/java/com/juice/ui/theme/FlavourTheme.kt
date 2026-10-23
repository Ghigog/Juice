package com.juice.ui.theme

import com.juice.core.progression.Flavour

/** Sound sets named after the design doc's audio column (§6.4). */
enum class SoundSet(val flavour: Flavour) {
    BRIGHT_CLICKS_AND_POPS(Flavour.ELECTRIC_CITRUS),
    SUB_BASS_AND_FIZZ(Flavour.BERRY_SURGE),
    METALLIC_CHIMES(Flavour.DRAGONFRUIT_OBSIDIAN),
    BELLS_AND_SPLASHES(Flavour.GOLDEN_HONEY),
}

/**
 * Colours (ARGB) and sound set used while a flavour is active: [accent] is the liquid and the
 * overlay's highlight, [deep] the bottom of the liquid. Golden Honey is [viscous]: slower slosh. The carbonated
 * flavours [fizz]: bubbles keep rising.
 */
data class FlavourTheme(val accent: Int, val deep: Int, val soundSet: SoundSet, val viscous: Boolean = false, val fizz: Boolean = true) {
    val reservoirColor: Int get() = accent

    companion object {
        fun of(flavour: Flavour): FlavourTheme = when (flavour) {
            Flavour.ELECTRIC_CITRUS -> FlavourTheme(0xFFFFB020.toInt(), 0xFFE8590C.toInt(), SoundSet.BRIGHT_CLICKS_AND_POPS)
            Flavour.BERRY_SURGE -> FlavourTheme(0xFFF0489E.toInt(), 0xFF6B0F5A.toInt(), SoundSet.SUB_BASS_AND_FIZZ)
            Flavour.DRAGONFRUIT_OBSIDIAN -> FlavourTheme(0xFFA78BFA.toInt(), 0xFF2A1250.toInt(), SoundSet.METALLIC_CHIMES)
            Flavour.GOLDEN_HONEY -> FlavourTheme(0xFFF5C542.toInt(), 0xFFB7791F.toInt(), SoundSet.BELLS_AND_SPLASHES, viscous = true, fizz = false)
        }
    }
}
