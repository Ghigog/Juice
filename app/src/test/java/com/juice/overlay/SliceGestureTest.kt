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

    @Test fun hitTestIsWithinTheTargetRadius() {
        assertTrue(overDismissTarget(100f, 900f, 100f, 900f, radius = 50f))
        assertTrue(overDismissTarget(130f, 940f, 100f, 900f, radius = 50f))
        assertFalse(overDismissTarget(160f, 900f, 100f, 900f, radius = 50f))
    }

    @Test fun movedReleaseOverTheTargetDismisses() {
        gesture.down(500f, 500f)
        gesture.hold()
        gesture.move(300f, 900f, radius = 90f)
        assertEquals(Release.DISMISS, gesture.up(overDismiss = true))
    }

    @Test fun movedReleaseElsewhereMoves() {
        gesture.down(500f, 500f)
        gesture.hold()
        gesture.move(300f, 900f, radius = 90f)
        assertEquals(Release.MOVED, gesture.up(overDismiss = false))
    }

    @Test fun swipeOverTheTargetDoesNotDismiss() {
        gesture.down(500f, 500f)
        gesture.move(600f, 500f, radius = 90f)
        assertEquals(Release.OPEN, gesture.up(overDismiss = true))
    }

    @Test fun cancelNeverDismisses() {
        gesture.down(500f, 500f)
        gesture.hold()
        gesture.cancel()
        assertEquals(Release.NONE, gesture.up(overDismiss = true))
    }
}
