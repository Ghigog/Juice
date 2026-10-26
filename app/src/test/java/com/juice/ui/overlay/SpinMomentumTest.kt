package com.juice.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinMomentumTest {
    private val frame = 16_000_000L

    private fun SpinMomentum.run(seconds: Int) = repeat(seconds * 1000 / 16) { step(frame) }

    private fun turnPerStep(m: SpinMomentum): Float {
        val before = m.angle
        m.step(frame)
        return (m.angle - before + 360f) % 360f
    }

    @Test fun idleTurnsAtTheSlowRate() {
        val m = SpinMomentum()
        m.step(1_000_000_000L)
        assertEquals(IDLE_SPIN_DEG_PER_SEC, m.angle, 0.01f)
    }

    @Test fun kickSpinsInItsDirection() {
        val up = SpinMomentum().apply { kick(300f); step(frame) }
        val down = SpinMomentum().apply { kick(-300f); step(frame) }
        assertTrue(up.angle > IDLE_SPIN_DEG_PER_SEC * 0.016f)
        assertTrue(down.boost < 0f && up.boost > 0f)
    }

    @Test fun fasterSwipeGivesBiggerBoost() {
        assertTrue(spinBoostFor(4000f) > spinBoostFor(1000f))
        val slow = SpinMomentum().apply { kick(spinBoostFor(1000f)) }
        val fast = SpinMomentum().apply { kick(spinBoostFor(2000f)) }
        assertTrue(fast.boost > slow.boost)
    }

    @Test fun boostDecaysBackToIdle() {
        val m = SpinMomentum().apply { kick(500f) }
        m.run(15)
        assertEquals(0f, m.boost, 0.0001f)
        assertEquals(IDLE_SPIN_DEG_PER_SEC * 0.016f, turnPerStep(m), 0.001f)
    }

    @Test fun boostIsCapped() {
        val m = SpinMomentum().apply { kick(1_000_000f) }
        assertEquals(MAX_SPIN_BOOST_DEG_PER_SEC, m.boost, 0.001f)
    }

    @Test fun reduceMotionStaysStill() {
        val m = SpinMomentum(enabled = false)
        m.kick(500f)
        m.run(2)
        assertEquals(0f, m.angle, 0f)
        assertEquals(0f, m.boost, 0f)
    }

    @Test fun grabZeroesBoostAndHoldsTheIdleTurn() {
        val m = SpinMomentum().apply { kick(500f); grab() }
        assertEquals(0f, m.boost, 0f)
        m.run(1)
        assertEquals(0f, m.angle, 0f)
    }

    @Test fun dragMovesTheAngleAndWraps() {
        val m = SpinMomentum().apply { grab(); drag(30f) }
        assertEquals(30f, m.angle, 0.001f)
        m.drag(-40f)
        assertEquals(350f, m.angle, 0.001f)
        m.drag(20f)
        assertEquals(10f, m.angle, 0.001f)
    }

    @Test fun releaseResumesTheIdleTurn() {
        val m = SpinMomentum().apply { grab(); release() }
        assertEquals(IDLE_SPIN_DEG_PER_SEC * 0.016f, turnPerStep(m), 0.001f)
    }

    @Test fun reduceMotionDoesNotDrag() {
        val m = SpinMomentum(enabled = false).apply { drag(30f) }
        assertEquals(0f, m.angle, 0f)
    }

    @Test fun onlyFastFlingsAreBigSwipes() {
        assertTrue(!isBigSwipe(BIG_SWIPE_PX_PER_SEC))
        assertTrue(isBigSwipe(BIG_SWIPE_PX_PER_SEC + 1f))
        assertTrue(isBigSwipe(-BIG_SWIPE_PX_PER_SEC - 1f))
    }
}
