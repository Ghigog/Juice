package com.juice.ui.overlay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayMotionTest {
    @Test
    fun theRevealLastsAsLongAsTheSliceFlight() {
        assertEquals(320, REVEAL_MILLIS)
    }

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

    @Test
    fun contentSwellsThenBurstsToNothing() {
        assertEquals(1f, popScale(0f), 0.001f)
        assertEquals(1.08f, popScale(0.3f), 0.001f)
        assertEquals(0f, popScale(1f), 0.001f)
        assertEquals(1f, popAlpha(0.3f), 0.001f)
        assertEquals(0f, popAlpha(1f), 0.001f)
    }

    @Test
    fun frothIsTheSameForTheSameSeed() {
        val origins = listOf(Offset(10f, 20f), Offset(100f, 200f))
        val first = frothBubbles(origins, seed = 7, maxDistance = 80f, maxRadius = 10f)
        assertEquals(first, frothBubbles(origins, seed = 7, maxDistance = 80f, maxRadius = 10f))
        assertTrue(first != frothBubbles(origins, seed = 8, maxDistance = 80f, maxRadius = 10f))
    }

    @Test
    fun frothStaysWithinBoundsAndShrinksAway() {
        val bubbles = frothBubbles(listOf(Offset(50f, 50f)), seed = 1, maxDistance = 80f, maxRadius = 10f)
        assertTrue(bubbles.isNotEmpty())
        bubbles.forEach {
            assertTrue(it.distance <= 80f && it.radius <= 10f && it.radius > 0f)
            assertEquals(0f, it.at(1f).second, 0.001f)
            assertEquals(it.origin, it.at(0f).first)
        }
    }

    @Test
    fun swayIsZeroAtBothEndsAndNonzeroBetween() {
        assertEquals(0f, entranceSway(0f), 0.0001f)
        assertEquals(0f, entranceSway(1f), 0.0001f)
        assertTrue(kotlin.math.abs(entranceSway(0.15f)) > 0.1f)
        assertTrue(entranceSway(0.15f) * entranceSway(0.5f) < 0f)
    }

    @Test fun cardPopsStartOneGapApartAndTheSequenceStaysShort() {
        assertEquals(listOf(0, CARD_POP_GAP_MILLIS, 2 * CARD_POP_GAP_MILLIS), List(3) { cardPopStartMillis(it) })
        assertEquals(0, cardPopTotalMillis(0))
        assertEquals(CARD_POP_MILLIS, cardPopTotalMillis(1))
        assertEquals(3 * CARD_POP_GAP_MILLIS + CARD_POP_MILLIS, cardPopTotalMillis(4))
    }

    @Test fun cardPopProgressRunsFromItsStartOverTheCardDuration() {
        assertEquals(0f, cardPopProgress(0f, 1), 0.001f)
        assertEquals(0f, cardPopProgress(CARD_POP_GAP_MILLIS.toFloat(), 1), 0.001f)
        assertEquals(0.5f, cardPopProgress(CARD_POP_GAP_MILLIS + CARD_POP_MILLIS / 2f, 1), 0.001f)
        assertEquals(1f, cardPopProgress(1000f, 1), 0.001f)
    }

    @Test fun elementPopsStartAfterTheLastCardOneGapApart() {
        assertEquals(0, elementPopStartMillis(0, 0))
        assertEquals(CARD_POP_GAP_MILLIS, elementPopStartMillis(0, 1))
        assertEquals(3 * CARD_POP_GAP_MILLIS, elementPopStartMillis(3, 0))
        assertEquals(4 * CARD_POP_GAP_MILLIS, elementPopStartMillis(3, 1))
        assertEquals(0f, elementPopProgress(3 * CARD_POP_GAP_MILLIS.toFloat(), 3, 0), 0.001f)
        assertEquals(0.5f, elementPopProgress(3 * CARD_POP_GAP_MILLIS + CARD_POP_MILLIS / 2f, 3, 0), 0.001f)
        assertEquals(1f, elementPopProgress(1000f, 3, 1), 0.001f)
    }
}
