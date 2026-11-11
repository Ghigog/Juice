package com.juice.feedback

import com.juice.core.progression.Flavour

/** Sound sets named after the design doc's audio column (§6.4). */
enum class SoundSet(val flavour: Flavour) {
    BRIGHT_CLICKS_AND_POPS(Flavour.ELECTRIC_CITRUS),
    SUB_BASS_AND_FIZZ(Flavour.BERRY_SURGE),
    METALLIC_CHIMES(Flavour.DRAGONFRUIT_OBSIDIAN),
    BELLS_AND_SPLASHES(Flavour.GOLDEN_HONEY),
}
