package com.juice.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeRightDetectorTest {
    private var fired = 0
    private val detector = SwipeRightDetector { fired++ }

    @Test fun rightSwipePastSlopFiresOnce() {
        detector.down(500f)
        detector.move(550f, slop = 20f)
        detector.move(700f, slop = 20f)
        assertEquals(1, fired)
    }

    @Test fun leftSwipeDoesNotFire() {
        detector.down(500f)
        detector.move(300f, slop = 20f)
        detector.up()
        assertEquals(0, fired)
    }

    @Test fun shortMoveDoesNotFire() {
        detector.down(500f)
        detector.move(515f, slop = 20f, tapSlop = 5f)
        detector.up()
        assertEquals(0, fired)
    }

    @Test fun aTapFires() {
        detector.down(500f)
        detector.move(498f, slop = 20f, tapSlop = 5f)
        detector.up()
        assertEquals(1, fired)
    }

    @Test fun aSwipeThenLiftFiresOnce() {
        detector.down(500f)
        detector.move(600f, slop = 20f)
        detector.up()
        assertEquals(1, fired)
    }

    @Test fun nextTouchCanFireAgain() {
        detector.down(500f); detector.move(600f, 20f)
        detector.down(500f); detector.move(600f, 20f)
        assertEquals(2, fired)
    }
}
