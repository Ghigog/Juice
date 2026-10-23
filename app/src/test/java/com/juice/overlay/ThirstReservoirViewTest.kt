package com.juice.overlay

import androidx.test.core.app.ApplicationProvider
import com.juice.ui.reservoir.ThirstReservoirView
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThirstReservoirViewTest {
    private fun view() = ThirstReservoirView(ApplicationProvider.getApplicationContext())

    private fun ThirstReservoirView.peak(seconds: Float = 0.5f): Float {
        var peak = 0f
        repeat((seconds * 60).toInt()) {
            stepSimulation(1f / 60)
            peak = maxOf(peak, waves.heights.maxOf { abs(it) })
        }
        return peak
    }

    @Test
    fun tiltMovesTheSurface() {
        val v = view()
        v.onAcceleration(-4f, 0f, 9f)
        assertTrue(v.peak() > 0.05f)
    }

    @Test
    fun shakeWavesAreLargerThanSlowTilt() {
        val tilt = view().apply { onAcceleration(-2f, 0f, 9.5f) }
        val shake = view().apply {
            onAcceleration(0f, 0f, 9.8f)
            onAcceleration(-25f, 0f, 9.8f)
        }
        assertTrue(shake.peak() > tilt.peak())
    }

    @Test
    fun reduceMotionKeepsTheLiquidStill() {
        val v = view().apply { sloshEnabled = false }
        v.onAcceleration(0f, 0f, 9.8f)
        v.onAcceleration(-25f, 5f, 9.8f)
        v.kick(3f)
        v.scrollImpulse(5f)
        v.disturb(0.5f, 0.5f, 0.5f)
        assertEquals(0f, v.peak())
        assertTrue(v.ripples.heights.all { it == 0f })
    }

    @Test
    fun viscousWavesTravelSlowerAndSettleFaster() {
        val plain = view().apply { kick(5f) }
        val honey = view().apply { viscous = true; kick(5f) }
        assertTrue(honey.waves.waveSpeed < plain.waves.waveSpeed)
        plain.peak(2f); honey.peak(2f)
        assertTrue(honey.peak(0.5f) < plain.peak(0.5f))
    }

    @Test
    fun touchDisturbsAndWakesRipples() {
        val v = view()
        v.disturb(0.5f, 0.5f, 0.5f)
        assertTrue(v.waves.heights.any { it != 0f })
        v.wake(0f, 0f, 1f, 1f)
        assertTrue(v.ripples.heights.count { it != 0f } > 2)
    }
}
