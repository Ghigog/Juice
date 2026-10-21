package com.juice.ui.logo

import com.juice.core.progression.Flavour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SliceLogoTest {
    @Test fun eachOpenItemIsASliceUpToAWholeFruit() {
        assertEquals(0, SliceLogoPainter.sliceCount(0))
        assertEquals(3, SliceLogoPainter.sliceCount(3))
        assertEquals(8, SliceLogoPainter.sliceCount(8))
        assertEquals(8, SliceLogoPainter.sliceCount(25))
    }

    @Test fun eachFlavourHasItsOwnFruit() {
        assertEquals(RimShape.Round, SliceStyle.of(Flavour.ELECTRIC_CITRUS).rimShape)
        assertNull(SliceStyle.of(Flavour.ELECTRIC_CITRUS).seed)
        assertEquals(0xFFFDE7F3.toInt(), SliceStyle.of(Flavour.BERRY_SURGE).seed)
        assertEquals(RimShape.Scales, SliceStyle.of(Flavour.DRAGONFRUIT_OBSIDIAN).rimShape)
        assertEquals(RimShape.Honeycomb, SliceStyle.of(Flavour.GOLDEN_HONEY).rimShape)
    }
}
