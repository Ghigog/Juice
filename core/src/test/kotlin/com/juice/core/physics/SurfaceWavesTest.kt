package com.juice.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceWavesTest {
    private val dt = 1f / 60

    private fun SurfaceWaves.maxAbs() = heights.maxOf { kotlin.math.abs(it) }

    private fun peakAfterKick(waves: SurfaceWaves, steps: Int): Float {
        var peak = 0f
        repeat(steps) { waves.step(dt); peak = maxOf(peak, waves.maxAbs()) }
        return peak
    }

    @Test
    fun calmSurfaceStaysAtZero() {
        val waves = SurfaceWaves()
        repeat(60) { waves.step(dt) }
        assertEquals(0f, waves.maxAbs(), 0f)
    }

    @Test
    fun disturbanceSpreadsToNeighboursThenSettles() {
        val waves = SurfaceWaves()
        waves.disturb(0.5f, 0.5f)
        val far = SurfaceWaves.COLUMNS / 2 + 8
        var reached = false
        repeat(30) { waves.step(dt); if (kotlin.math.abs(waves.heights[far]) > 1e-3f) reached = true }
        assertTrue(reached)
        repeat(600) { waves.step(dt) }
        assertEquals(0f, waves.maxAbs(), 1e-3f)
    }

    @Test
    fun viscousSurfaceIsSlowerAndSettlesSooner() {
        val plain = SurfaceWaves()
        val thick = SurfaceWaves().apply { viscosity = 1f }
        assertTrue(thick.waveSpeed < plain.waveSpeed)
        plain.kick(0.5f); thick.kick(0.5f)
        repeat(30) { plain.step(dt); thick.step(dt) }
        assertTrue(thick.maxAbs() < plain.maxAbs())
        repeat(120) { plain.step(dt); thick.step(dt) }
        assertTrue(thick.maxAbs() < plain.maxAbs())
    }

    @Test
    fun largeKickPeaksHigherThanSmallTilt() {
        val kicked = SurfaceWaves().apply { kick(1f) }
        val tilted = SurfaceWaves().apply { tiltX = 0.1f; tiltY = 0.1f }
        assertTrue(peakAfterKick(kicked, 60) > peakAfterKick(tilted, 60))
    }

    @Test
    fun tiltBiasesTheSurface() {
        val waves = SurfaceWaves().apply { tiltX = 1f }
        repeat(300) { waves.step(dt) }
        assertTrue(waves.heights.last() > 0f && waves.heights.first() < 0f)
    }

    @Test
    fun valuesStayBoundedWithHugeFrameTime() {
        val waves = SurfaceWaves().apply { tiltX = 1f; tiltY = 1f }
        waves.kick(100f)
        repeat(100) { waves.step(1f) }
        assertTrue(waves.heights.all { it.isFinite() && it in -1f..1f })
    }
}
