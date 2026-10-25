package com.juice.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloseRequestTest {
    private var finished = 0
    private var reduceMotion = false
    private val close = CloseRequest(reduceMotion = { reduceMotion }, finish = { finished++ })

    @Test
    fun handleWaitsForTheAnimationAndASecondRequestIsIgnored() {
        close.request()
        close.request()
        assertTrue(close.closing.value)
        assertEquals(0, finished)
        close.closed()
        close.closed()
        assertEquals(1, finished)
        assertTrue(!close.closing.value)
    }

    @Test
    fun reducedMotionFinishesAtOnce() {
        reduceMotion = true
        close.request()
        assertEquals(1, finished)
        assertTrue(!close.closing.value)
    }

    @Test
    fun tearDownMidCloseNeverShowsTheHandle() {
        close.request()
        close.reset()
        close.closed()
        assertEquals(0, finished)
    }
}
