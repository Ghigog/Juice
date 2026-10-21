package com.juice.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SliceGestureTest {
    private val gesture = SliceGesture(moveSlop = 10f)

    @Test fun swipePastTheRadiusAnyWayOpens() {
        listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, -0.7f to 0.7f).forEach { (x, y) ->
            gesture.down(500f, 500f)
            gesture.move(500f + 100f * x, 500f + 100f * y, radius = 90f)
            assertEquals(Release.OPEN, gesture.up(), "direction $x,$y")
        }
    }

    @Test fun swipeShortOfTheRadiusDoesNotOpen() {
        gesture.down(500f, 500f)
        gesture.move(560f, 500f, radius = 90f)
        assertEquals(Release.NONE, gesture.up())
    }

    @Test fun comingBackInsideTheRadiusBeforeLiftingDoesNotOpen() {
        gesture.down(500f, 500f)
        gesture.move(600f, 500f, radius = 90f)
        gesture.move(520f, 500f, radius = 90f)
        assertEquals(Release.NONE, gesture.up())
    }

    @Test fun crossingTheRadiusIsReportedOnce() {
        gesture.down(500f, 500f)
        assertFalse(gesture.move(550f, 500f, radius = 90f))
        assertTrue(gesture.move(595f, 500f, radius = 90f))
        assertFalse(gesture.move(620f, 500f, radius = 90f))
    }

    @Test fun aTapDoesNothing() {
        gesture.down(500f, 500f)
        gesture.move(503f, 501f, radius = 90f)
        assertEquals(Release.NONE, gesture.up())
    }

    @Test fun holdingStillTurnsThePressIntoAMove() {
        gesture.down(500f, 500f)
        assertTrue(gesture.hold())
        gesture.move(800f, 900f, radius = 90f)
        assertEquals(300f, gesture.dx)
        assertEquals(400f, gesture.dy)
        assertEquals(Release.MOVED, gesture.up())
    }

    @Test fun aHoldAfterTheSwipeStartedIsIgnored() {
        gesture.down(500f, 500f)
        gesture.move(540f, 500f, radius = 90f)
        assertFalse(gesture.hold())
        gesture.move(620f, 500f, radius = 90f)
        assertEquals(Release.OPEN, gesture.up())
    }

    @Test fun pullGrowsToOneAtTheRadius() {
        gesture.down(0f, 0f)
        gesture.move(45f, 0f, radius = 90f)
        assertEquals(0.5f, gesture.pull(90f))
        gesture.move(300f, 0f, radius = 90f)
        assertEquals(1f, gesture.pull(90f))
    }
}
