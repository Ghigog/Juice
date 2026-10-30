package com.juice.core.liquid

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiquidFrameTest {
    private fun shaken() = LiquidEngine(200f, 260f, Random(4)).apply {
        pour(200)
        repeat(90) { setGravity(if (it % 6 < 3) 1.6f else -1.6f, 1f, 0f); tick() }
    }

    @Test fun aFrameCopiesParticlesBubblesAndPops() {
        val e = shaken()
        val f = LiquidFrame().apply { copyFrom(e) }
        assertEquals(e.sim.count, f.count)
        assertEquals(e.sim.p.h, f.h)
        assertEquals(e.sim.x[7], f.x[7]); assertEquals(e.sim.foam[7], f.foam[7])
        assertEquals(kotlin.math.hypot(e.sim.vx[7], e.sim.vy[7]), f.speed[7])
        assertEquals(e.froth.bubbles.size, f.bubbles)
        assertTrue(f.bubbles + f.pops > 0)
        if (f.bubbles > 0) assertEquals(e.froth.bubbleX(e.froth.bubbles[0]), f.bubbleX[0])
    }

    @Test fun theUiSeesOnlyFinishedFramesAndKeepsItsOwnUntilANewOne() {
        val frames = LiquidFrames()
        val e = shaken()
        frames.publish(e)
        val first = frames.latest()
        assertEquals(e.sim.count, first.count)
        // No new frame: the UI keeps the one it has.
        assertSame(first, frames.latest())
        e.tick(); frames.publish(e)
        val second = frames.latest()
        assertNotSame(first, second)
        assertEquals(e.sim.x[0], second.x[0])
    }
}
