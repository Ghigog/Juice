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

    @Test fun flickSpinsLikeTheDragItReleases() {
        assertEquals(-DRAG_DEG_PER_PX * 1000f, spinForFling(1000f), 0.001f)
        assertEquals(DRAG_DEG_PER_PX * 1000f, spinForFling(-1000f), 0.001f)
        assertEquals(0f, spinForFling(0f), 0f)
    }

    @Test fun flickSpinIsCapped() {
        assertEquals(MAX_SPIN_BOOST_DEG_PER_SEC, spinForFling(-1_000_000f), 0f)
        assertEquals(-MAX_SPIN_BOOST_DEG_PER_SEC, spinForFling(1_000_000f), 0f)
    }

    @Test fun staysStillWhenNothingIsTouched() {
        val m = SpinMomentum()
        m.run(10)
        assertEquals(0f, m.angle, 0f)
    }

    @Test fun kickSpinsInItsDirection() {
        val up = SpinMomentum().apply { kick(300f); step(frame) }
        val down = SpinMomentum().apply { kick(-300f); step(frame) }
        assertTrue(up.angle > 0f && down.angle > 180f)
        assertTrue(down.boost < 0f && up.boost > 0f)
    }

    @Test fun boostDecaysToRest() {
        val m = SpinMomentum().apply { kick(500f) }
        m.run(15)
        assertEquals(0f, m.boost, 0.0001f)
        assertEquals(0f, turnPerStep(m), 0.001f)
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

    @Test fun grabZeroesBoostAndHoldsTheSlice() {
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

    @Test fun reduceMotionDoesNotDrag() {
        val m = SpinMomentum(enabled = false).apply { drag(30f) }
        assertEquals(0f, m.angle, 0f)
    }

    @Test fun pullSpinFollowsTheDirectionOfThePull() {
        assertTrue(spinForPull(2000f, 100f) > 0f)
        assertTrue(spinForPull(-2000f, 100f) < 0f)
        assertTrue(spinForPull(100f, 2000f) > 0f)
        assertTrue(spinForPull(100f, -2000f) < 0f)
    }

    @Test fun harderPullSpinsFasterUpToTheCap() {
        assertTrue(spinForPull(3000f, 0f) > spinForPull(1000f, 0f))
        assertEquals(MAX_SPIN_BOOST_DEG_PER_SEC, spinForPull(1_000_000f, 0f), 0f)
        assertEquals(-MAX_SPIN_BOOST_DEG_PER_SEC, spinForPull(0f, -1_000_000f), 0f)
        assertEquals(0f, spinForPull(0f, 0f), 0f)
    }
}
