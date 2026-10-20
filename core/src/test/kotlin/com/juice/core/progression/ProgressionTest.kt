package com.juice.core.progression

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionTest {
    @Test
    fun zeroJuiceIsLevelOne() = assertEquals(1, Progression.levelFor(0))

    @Test
    fun levelChangesExactlyAtThreshold() {
        assertEquals(1, Progression.levelFor(99))
        assertEquals(2, Progression.levelFor(100))
        assertEquals(3, Progression.levelFor(899))
        assertEquals(4, Progression.levelFor(900))
        assertEquals(5, Progression.levelFor(1600))
    }

    @Test
    fun levelOnePlayerGetsElectricCitrus() =
        assertEquals(Flavour.ELECTRIC_CITRUS, Progression.flavourForJuice(0))

    @Test
    fun levelFivePlayerGetsBerrySurge() =
        assertEquals(Flavour.BERRY_SURGE, Progression.flavourForJuice(Progression.thresholdFor(5)))

    @Test
    fun flavoursSwitchAtLevels1_5_10_25() {
        assertEquals(Flavour.ELECTRIC_CITRUS, Progression.flavourFor(4))
        assertEquals(Flavour.BERRY_SURGE, Progression.flavourFor(9))
        assertEquals(Flavour.DRAGONFRUIT_OBSIDIAN, Progression.flavourFor(10))
        assertEquals(Flavour.DRAGONFRUIT_OBSIDIAN, Progression.flavourFor(24))
        assertEquals(Flavour.GOLDEN_HONEY, Progression.flavourFor(25))
    }
}
