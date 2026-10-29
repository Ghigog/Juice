package com.juice.ui.overlay

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDrainTest {
    private val start = Rect(100f, 400f, 300f, 800f)
    private val end = Rect(340f, 20f, 384f, 64f)

    @Test
    fun theGhostStartsAtTheCardFullSizeAndEndsInTheButtonSmallAndGone() {
        val first = drainFrame(start, end, 0f)
        assertEquals(start.center, first.offset)
        assertEquals(1f, first.scale, 0f)
        assertEquals(1f, first.alpha, 0f)
        val last = drainFrame(start, end, 1f)
        assertEquals(end.center.x, last.offset.x, 0.01f)
        assertEquals(end.center.y, last.offset.y, 0.01f)
        assertEquals(DRAIN_END_SCALE, last.scale, 0.001f)
        assertEquals(0f, last.alpha, 0.001f)
    }

    @Test
    fun theGhostSpeedsUpLikeADrain() {
        val half = drainFrame(start, end, 0.5f).offset
        val travelled = (half - start.center).getDistance()
        val whole = (end.center - start.center).getDistance()
        assertTrue(travelled < whole * 0.5f)
    }

    @Test
    fun thePathBowsAwayFromTheStraightLine() {
        val mid = drainFrame(start, end, 0.6f).offset
        val a = start.center
        val b = end.center
        val cross = (b.x - a.x) * (mid.y - a.y) - (b.y - a.y) * (mid.x - a.x)
        assertTrue(kotlin.math.abs(cross) > 1f)
    }

    @Test
    fun theGhostStaysSolidUntilNearTheEnd() {
        assertEquals(1f, drainFrame(start, end, 0.7f).alpha, 0f)
        assertTrue(drainFrame(start, end, 0.9f).alpha < 1f)
    }
}
