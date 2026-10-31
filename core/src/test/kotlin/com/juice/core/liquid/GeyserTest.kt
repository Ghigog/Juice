package com.juice.core.liquid

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeyserTest {
    private fun armed(seed: Int = 1) = Geyser(Random(seed)).apply { armed = true }

    /** Steps [frames] frames at [shake]; returns how many of them burst. */
    private fun Geyser.run(frames: Int, shake: Float = 0f): Int {
        var bursts = 0
        repeat(frames) { if (step(shake, 300f)) bursts++ }
        return bursts
    }

    @Test fun anUnarmedGeyserIgnoresShaking() {
        val g = Geyser(Random(1))
        assertEquals(0, g.run(600, shake = 3f))
        assertEquals(0f, g.pressure)
        assertFalse(g.prompting)
        assertFalse(g.busy)
    }

    @Test fun aFullMeterPromptsToShake() {
        val g = armed()
        assertTrue(g.prompting)
        assertEquals(Geyser.Phase.IDLE, g.phase)
    }

    @Test fun aTrembleBuildsNoPressure() {
        val g = armed()
        g.run(600, shake = Geyser.SHAKE_MIN)
        assertEquals(0f, g.pressure)
    }

    @Test fun hardShakingBurstsOnceAndSpraysOutOfTheTop() {
        val g = armed()
        assertEquals(0, g.run(60, shake = 3f))
        assertEquals(Geyser.Phase.BUILDING, g.phase)
        assertTrue(g.pressure in 0.3f..0.9f)
        assertEquals(1, g.run(120, shake = 3f))
        assertEquals(Geyser.Phase.SPURTING, g.phase)
        assertFalse(g.prompting)
        g.run(5)
        assertTrue(g.drops.isNotEmpty())
        assertTrue(g.drops.all { it.y < 0f }, "drops leave through the top")
    }

    @Test fun stoppingLetsThePressureLeakAway() {
        val g = armed()
        g.run(60, shake = 3f)
        g.run(600)
        assertEquals(0f, g.pressure)
        assertEquals(Geyser.Phase.IDLE, g.phase)
    }

    @Test fun afterTheSpurtItSettlesAndCanBeShakenAgain() {
        val g = armed()
        g.run(200, shake = 3f)
        g.run(400)
        assertEquals(Geyser.Phase.IDLE, g.phase)
        assertTrue(g.drops.isEmpty())
        assertTrue(g.prompting)
        assertEquals(1, g.run(200, shake = 3f))
    }

    @Test fun droppingBelowFullStopsThePromptAndLetsDropsLand() {
        val g = armed()
        g.run(200, shake = 3f)
        assertTrue(g.drops.isNotEmpty())
        g.armed = false
        assertFalse(g.prompting)
        assertEquals(0f, g.pressure)
        assertEquals(0, g.run(400, shake = 3f))
        assertTrue(g.drops.isEmpty())
        assertEquals(Geyser.Phase.IDLE, g.phase)
        assertFalse(g.busy)
    }

    @Test fun theSameSeedSpraysTheSameDrops() {
        val a = armed(7); val b = armed(7)
        a.run(200, shake = 3f); b.run(200, shake = 3f)
        assertEquals(a.drops.size, b.drops.size)
        assertEquals(a.drops.map { it.x to it.y }, b.drops.map { it.x to it.y })
    }
}
