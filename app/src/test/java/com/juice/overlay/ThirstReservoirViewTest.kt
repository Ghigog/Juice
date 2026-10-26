package com.juice.overlay

import androidx.test.core.app.ApplicationProvider
import android.view.Surface
import com.juice.ui.reservoir.Gravity
import com.juice.ui.reservoir.ThirstReservoirView
import com.juice.ui.reservoir.liquidLevel
import com.juice.ui.reservoir.screenGravity
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
    fun turningThePhoneSloshesTheSurface() {
        val v = view()
        v.onAcceleration(-4f, 8f, 0f)
        assertTrue(v.peak() > 0.05f)
    }

    @Test
    fun aPhoneLyingFlatDoesNotJitter() {
        val v = view()
        repeat(120) { v.onAcceleration(0.05f * (it % 3 - 1), 0.04f * (it % 2 - 1), 9.8f); v.stepSimulation(1f / 60) }
        assertEquals(0f, v.peak(), 1e-4f)
    }

    private fun gravity(ax: Float, ay: Float, az: Float = 0f) = screenGravity(ax, ay, az, Surface.ROTATION_0)

    @Test
    fun gravityPointsToTheSideOfTheScreenThatIsDown() {
        assertEquals(Gravity(0f, 1f), gravity(0f, 9.8f))
        assertEquals(Gravity(0f, -1f), gravity(0f, -9.8f))
        assertTrue(gravity(-9.8f, 0f).x > 0.99f)
        assertTrue(gravity(9.8f, 0f).x < -0.99f)
    }

    @Test
    fun gravityFollowsTheDisplayRotation() {
        // Landscape (rotated counter-clockwise): the device's +x edge is the top of the screen.
        val g = screenGravity(9.8f, 0f, 0f, Surface.ROTATION_90)
        assertEquals(0f, g.x, 1e-4f)
        assertEquals(1f, g.y, 1e-4f)
        assertEquals(-1f, screenGravity(-9.8f, 0f, 0f, Surface.ROTATION_90).y, 1e-4f)
        val h = screenGravity(0f, 9.8f, 0f, Surface.ROTATION_180)
        assertEquals(-1f, h.y, 1e-4f)
    }

    @Test
    fun aPhoneLyingFlatKeepsTheLiquidOnTheBottom() {
        assertEquals(Gravity.DOWN, gravity(0.2f, -0.3f, 9.8f))
    }

    @Test
    fun liquidLevelRestsFlatOnTheBottomOrTop() {
        assertEquals(0.6f, liquidLevel(Gravity.DOWN, 0.4f, 0.5f), 1e-4f)
        assertEquals(-0.4f, liquidLevel(Gravity(0f, -1f), 0.4f, 0.5f), 1e-4f)
    }

    @Test
    fun tiltedLiquidKeepsItsVolumeAndGathersOnTheLowSide() {
        val aspect = 0.5f
        for (side in listOf(-1f, 1f)) {
            val g = screenGravity(-side * 4f, 8f, 0f, Surface.ROTATION_0)
            val level = liquidLevel(g, 0.4f, aspect)
            var inside = 0
            var low = 0
            val n = 200
            for (i in 0 until n) for (j in 0 until n) {
                val x = (i + 0.5f) / n * aspect
                val y = (j + 0.5f) / n
                if (x * g.x + y * g.y >= level) {
                    inside++
                    if ((x < aspect / 2) == (side < 0)) low++
                }
            }
            assertEquals(0.4f, inside / (n * n).toFloat(), 0.01f)
            assertTrue(low > inside - low)
        }
    }

    @Test
    fun shakeWavesAreLargerThanSlowTilt() {
        val tilt = view().apply { onAcceleration(-2f, 9.5f, 0f) }
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

    @Test
    fun touchBurstsBubblesOnlyWhenFizzyAndParticlesAreOn() {
        fun touched(fizz: Boolean, particles: Boolean) = view().apply {
            this.fizz = fizz; particlesEnabled = particles; fill = 0.4f
            disturb(0.5f, 0.5f, 0.5f)
        }.particleCount
        assertTrue(touched(fizz = true, particles = true) > 0)
        assertEquals(0, touched(fizz = false, particles = true))
        assertEquals(0, touched(fizz = true, particles = false))
    }
}
