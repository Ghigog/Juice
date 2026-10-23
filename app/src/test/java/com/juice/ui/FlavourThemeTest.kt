package com.juice.ui

import com.juice.core.progression.Flavour
import com.juice.ui.theme.FlavourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class FlavourThemeTest {
    @Test
    fun onlyGoldenHoneyIsFlat() {
        assertEquals(
            mapOf(
                Flavour.ELECTRIC_CITRUS to true,
                Flavour.BERRY_SURGE to true,
                Flavour.DRAGONFRUIT_OBSIDIAN to true,
                Flavour.GOLDEN_HONEY to false,
            ),
            Flavour.entries.associateWith { FlavourTheme.of(it).fizz },
        )
    }
}
