package com.juice.core.physics

import kotlin.math.PI
import kotlin.math.cos

/**
 * 1D height field for the reservoir surface. [heights] holds [COLUMNS] values clamped to -1..1, left to right.
 * The surface rests flat; [kick] and [disturb] add waves; [viscosity] (0..1) slows
 * waves and damps them faster. Arrays are allocated once; [step] does not allocate.
 */
class SurfaceWaves {
    private val h = FloatArray(COLUMNS)
    private val v = FloatArray(COLUMNS)

    /** Read-only view for the renderer; do not modify. */
    val heights: FloatArray get() = h

    var viscosity = 0f
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Wave speed in columns per second at the current [viscosity]. */
    val waveSpeed: Float get() = BASE_SPEED * (1f - 0.6f * viscosity)

    /** Adds a standing wave across the whole surface; the sign sets the direction. */
    fun kick(strength: Float) {
        for (i in 0 until COLUMNS) v[i] += strength * KICK_GAIN * cos(PI.toFloat() * i / (COLUMNS - 1))
    }

    /** Adds a local wave at [x] (0..1 across the surface), e.g. for a tap. */
    fun disturb(x: Float, strength: Float) {
        val centre = x.coerceIn(0f, 1f) * (COLUMNS - 1)
        val first = (centre - 1.5f).toInt().coerceAtLeast(0)
        val last = (centre + 1.5f).toInt().coerceAtMost(COLUMNS - 1)
        for (i in first..last) {
            val d = (i - centre) / 1.5f
            if (d > -1f && d < 1f) h[i] = (h[i] + strength * (1f - d * d)).coerceIn(-1f, 1f)
        }
    }

    fun step(dt: Float) {
        val capped = dt.coerceIn(0f, MAX_DT)
        val steps = (capped / SUB_DT).toInt() + 1
        val sub = capped / steps
        val c2 = waveSpeed * waveSpeed
        val damping = BASE_DAMPING + 5f * viscosity
        repeat(steps) {
            for (i in 0 until COLUMNS) {
                val left = h[if (i == 0) 1 else i - 1]
                val right = h[if (i == COLUMNS - 1) COLUMNS - 2 else i + 1]
                val a = c2 * (left + right - 2f * h[i]) - RESTORE * h[i] - damping * v[i]
                v[i] += a * sub
            }
            for (i in 0 until COLUMNS) h[i] = (h[i] + v[i] * sub).coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val COLUMNS = 48
        const val MAX_DT = 1f / 30
        private const val SUB_DT = 1f / 120
        private const val BASE_SPEED = 30f
        private const val BASE_DAMPING = 3f
        private const val RESTORE = 20f
        private const val KICK_GAIN = 6f
    }
}
