package com.juice.ui.overlay

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sign

/** The most the opening pull can spin the slice, in degrees per second. */
internal const val MAX_SPIN_BOOST_DEG_PER_SEC = 720f

/** How fast the boost fades: it falls by 1/e every this many seconds. */
internal const val SPIN_FRICTION_SECONDS = 1.2f

private const val DEG_PER_SEC_PER_PX_PER_SEC = 0.25f

/** How far the slice turns per pixel the finger drags it. */
internal const val DRAG_DEG_PER_PX = 0.3f

/**
 * The signed spin, in degrees per second, for an opening pull moving at ([vx], [vy]) px/s: the
 * speed of the pull, turning clockwise when it runs mostly right or down and anticlockwise when
 * mostly left or up, capped at [MAX_SPIN_BOOST_DEG_PER_SEC].
 */
internal fun spinForPull(vx: Float, vy: Float): Float {
    val along = if (abs(vx) >= abs(vy)) vx else vy
    val speed = hypot(vx, vy) * DEG_PER_SEC_PER_PX_PER_SEC
    return (sign(along) * speed).coerceIn(-MAX_SPIN_BOOST_DEG_PER_SEC, MAX_SPIN_BOOST_DEG_PER_SEC)
}

/**
 * The signed spin, in degrees per second, for a carousel flick released at [vy] px/s: the same sign
 * and scale as dragging ([DRAG_DEG_PER_PX], so a downward flick turns like a downward drag), capped
 * at [MAX_SPIN_BOOST_DEG_PER_SEC].
 */
internal fun spinForFling(vy: Float): Float =
    (-vy * DRAG_DEG_PER_PX).coerceIn(-MAX_SPIN_BOOST_DEG_PER_SEC, MAX_SPIN_BOOST_DEG_PER_SEC)

/**
 * The backdrop slice's rotation: still unless dragged, or spun by a [kick] (the opening pull) that
 * decays exponentially back to rest. Pure, so it can be stepped by a frame loop or a test. When not
 * [enabled] (reduce motion) it never turns.
 */
internal class SpinMomentum(
    val enabled: Boolean = true,
    private val maxBoostDegPerSec: Float = MAX_SPIN_BOOST_DEG_PER_SEC,
    private val frictionSeconds: Float = SPIN_FRICTION_SECONDS,
) {
    var boost = 0f
        private set
    var angle = 0f
        private set

    private var held = false

    /** A finger is down: stops any spin and holds the slice so it follows the finger. */
    fun grab() {
        held = true
        boost = 0f
    }

    /** The finger is up: the slice may spin again. */
    fun release() {
        held = false
    }

    /** Turns the slice directly by [deg] (sign = scroll direction). */
    fun drag(deg: Float) {
        if (!enabled) return
        angle = ((angle + deg) % 360f + 360f) % 360f
    }

    /** Sets the spin to [degPerSec] (sign = direction), so a slower kick lets the spin ease down to it. */
    fun kick(degPerSec: Float) {
        if (!enabled) return
        boost = degPerSec.coerceIn(-maxBoostDegPerSec, maxBoostDegPerSec)
    }

    /** Starts the slice at [deg] degrees, so it carries on from another slice's angle. */
    fun seed(deg: Float) {
        if (!enabled) return
        angle = (deg % 360f + 360f) % 360f
    }

    /** Advances by [dtNanos] and returns the angle in degrees, in [0, 360). */
    fun step(dtNanos: Long): Float {
        if (!enabled) return 0f
        if (held) return angle
        val dt = dtNanos / 1e9f
        angle = ((angle + boost * dt) % 360f + 360f) % 360f
        boost *= exp(-dt / frictionSeconds)
        if (abs(boost) < 0.01f) boost = 0f
        return angle
    }
}
