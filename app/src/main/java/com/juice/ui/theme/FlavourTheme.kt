package com.juice.ui.theme

import com.juice.core.progression.Flavour

/** Sound sets named after the design doc's audio column (§6.4). */
enum class SoundSet { BRIGHT_CLICKS_AND_POPS, SUB_BASS_AND_FIZZ, METALLIC_CHIMES, BELLS_AND_SPLASHES }

/** Reservoir colour (ARGB) and sound set used while a flavour is active. */
data class FlavourTheme(val reservoirColor: Int, val soundSet: SoundSet) {
    companion object {
        fun of(flavour: Flavour): FlavourTheme = when (flavour) {
            Flavour.ELECTRIC_CITRUS -> FlavourTheme(0xFFFF9F1C.toInt(), SoundSet.BRIGHT_CLICKS_AND_POPS)
            Flavour.BERRY_SURGE -> FlavourTheme(0xFFC2185B.toInt(), SoundSet.SUB_BASS_AND_FIZZ)
            Flavour.DRAGONFRUIT_OBSIDIAN -> FlavourTheme(0xFF4A148C.toInt(), SoundSet.METALLIC_CHIMES)
            Flavour.GOLDEN_HONEY -> FlavourTheme(0xFFFFC107.toInt(), SoundSet.BELLS_AND_SPLASHES)
        }
    }
}
