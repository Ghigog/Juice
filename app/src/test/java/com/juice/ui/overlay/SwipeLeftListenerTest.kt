package com.juice.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeLeftListenerTest {
    private var fired = 0
    private val listener = SwipeLeftListener(100.0) { fired++ }

    @Test
    fun staysQuietBelowTheSlop() {
        listener.down(300.0, 50.0)
        listener.move(250.0, 50.0)
        listener.up(210.0, 50.0)
        assertEquals(0, fired)
    }

    @Test
    fun firesOncePastTheSlopAndNotOnFurtherMovesOrTheLift() {
        listener.down(300.0, 50.0)
        listener.move(250.0, 50.0)
        assertEquals(0, fired)
        listener.move(190.0, 52.0)
        assertEquals(1, fired)
        listener.move(120.0, 50.0)
        listener.up(100.0, 50.0)
        assertEquals(1, fired)
    }

    @Test
    fun aNewDownRearmsIt() {
        listener.down(300.0, 50.0)
        listener.up(150.0, 50.0)
        listener.down(300.0, 80.0)
        listener.move(200.0, 80.0)
        listener.up(120.0, 80.0)
        assertEquals(2, fired)
    }

    @Test
    fun aRightwardStrokeDoesNotFire() {
        listener.down(100.0, 50.0)
        listener.move(200.0, 50.0)
        listener.up(350.0, 50.0)
        assertEquals(0, fired)
    }

    @Test
    fun aMostlyVerticalStrokeDoesNotFire() {
        listener.down(300.0, 0.0)
        listener.move(250.0, 200.0)
        listener.up(180.0, 400.0)
        assertEquals(0, fired)
    }
}
