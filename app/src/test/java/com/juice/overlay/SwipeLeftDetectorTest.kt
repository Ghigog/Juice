package com.juice.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeLeftDetectorTest {
    private var fired = 0
    private val detector = SwipeLeftDetector { fired++ }

    @Test fun leftSwipePastSlopFiresOnce() {
        detector.down(500f)
        detector.move(450f, slop = 20f)
        detector.move(300f, slop = 20f)
        assertEquals(1, fired)
    }

    @Test fun rightSwipeDoesNotFire() {
        detector.down(500f)
        detector.move(700f, slop = 20f)
        detector.up()
        assertEquals(0, fired)
    }

    @Test fun shortMoveDoesNotFire() {
        detector.down(500f)
        detector.move(485f, slop = 20f, tapSlop = 5f)
        detector.up()
        assertEquals(0, fired)
    }

    @Test fun aTapFires() {
        detector.down(500f)
        detector.move(502f, slop = 20f, tapSlop = 5f)
        detector.up()
        assertEquals(1, fired)
    }

    @Test fun aSwipeThenLiftFiresOnce() {
        detector.down(500f)
        detector.move(400f, slop = 20f)
        detector.up()
        assertEquals(1, fired)
    }

    @Test fun nextTouchCanFireAgain() {
        detector.down(500f); detector.move(400f, 20f)
        detector.down(500f); detector.move(400f, 20f)
        assertEquals(2, fired)
    }
}
