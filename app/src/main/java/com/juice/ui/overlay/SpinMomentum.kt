package com.juice.ui.overlay

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** The backdrop slice turns once in this long when nothing is scrolling. */
internal const val BACKDROP_TURN_MILLIS = 45_000

/** Idle turn rate in degrees per second. */
internal const val IDLE_SPIN_DEG_PER_SEC = 360f * 1000f / BACKDROP_TURN_MILLIS

/** The most a fling can add to the idle rate, in degrees per second. */
internal const val MAX_SPIN_BOOST_DEG_PER_SEC = 720f

/** How fast the boost fades: it falls by 1/e every this many seconds. */
internal const val SPIN_FRICTION_SECONDS = 1.2f

private const val DEG_PER_SEC_PER_PX_PER_SEC = 0.25f

/** The spin boost for a carousel scrolling at [pxPerSec] (positive = down); capped later by [SpinMomentum]. */
internal fun spinBoostFor(pxPerSec: Float): Float = pxPerSec * DEG_PER_SEC_PER_PX_PER_SEC

/**
 * The backdrop slice's rotation: a steady idle turn plus a boost from carousel flings that decays
 * exponentially back to zero. Pure, so it can be stepped by a frame loop or a test. When not
 * [enabled] (reduce motion) it never turns.
 */
internal class SpinMomentum(
    val enabled: Boolean = true,
    private val idleDegPerSec: Float = IDLE_SPIN_DEG_PER_SEC,
    private val maxBoostDegPerSec: Float = MAX_SPIN_BOOST_DEG_PER_SEC,
    private val frictionSeconds: Float = SPIN_FRICTION_SECONDS,
) {
    var boost = 0f
        private set
    var angle = 0f
        private set

    /** Adds a spin of [degPerSec] (sign = direction); a slower kick never cuts a faster spin the same way. */
    fun kick(degPerSec: Float) {
        if (!enabled) return
        val v = degPerSec.coerceIn(-maxBoostDegPerSec, maxBoostDegPerSec)
        if (sign(v) != sign(boost) || abs(v) > abs(boost)) boost = v
    }

    /** Advances by [dtNanos] and returns the angle in degrees, in [0, 360). */
    fun step(dtNanos: Long): Float {
        if (!enabled) return 0f
        val dt = dtNanos / 1e9f
        angle = ((angle + (idleDegPerSec + boost) * dt) % 360f + 360f) % 360f
        boost *= exp(-dt / frictionSeconds)
        if (abs(boost) < 0.01f) boost = 0f
        return angle
    }
}
