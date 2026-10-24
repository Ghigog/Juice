package com.juice.ui.overlay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayMotionTest {
    @Test
    fun cardsStartOneAfterAnother() {
        assertEquals(0, entranceDelayMillis(0))
        assertTrue(entranceDelayMillis(2) > entranceDelayMillis(1))
    }

    @Test
    fun theDelayIsBoundedForLongListsAndBadIndexes() {
        assertEquals(entranceDelayMillis(MAX_STAGGERED_ITEMS - 1), entranceDelayMillis(500))
        assertEquals(0, entranceDelayMillis(-3))
    }

    @Test
    fun everyItemIsInPlaceWhenTheEntranceEnds() {
        for (i in 0..20) assertEquals(1f, entranceFraction(1f, i), 0f)
    }

    @Test
    fun laterItemsHaveNotStartedWhenTheEntranceBegins() {
        assertEquals(0f, entranceFraction(0f, 0), 0f)
        assertEquals(0f, entranceFraction(0.05f, 3), 0f)
    }

    @Test
    fun theRevealRadiusReachesTheFarthestCorner() {
        val origin = revealOrigin(width = 400f, overshoot = 70f)
        assertEquals(Offset(470f, -70f), origin)
        // Farthest corner is bottom-left: (400 + 70 - 0, 800 + 70).
        assertEquals(kotlin.math.hypot(470f, 870f), revealRadius(400f, 800f, origin), 0.01f)
    }
}
