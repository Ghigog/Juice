package com.juice.core.liquid

import kotlin.math.abs
import kotlin.math.min

/** Gravity in g: [x] right and [y] down the screen, [z] into it (0 upright, 1 lying flat). */
data class ScreenGravity(val x: Float, val y: Float, val z: Float) {
    companion object {
        /** Standard gravity in m/s², as Android's SensorManager.GRAVITY_EARTH. */
        const val EARTH = 9.80665f

        /**
         * From an accelerometer reading ([ax], [ay], [az] in m/s², device axes, y up) and the display rotation in
         * [quarterTurns] counter-clockwise (Surface.ROTATION_0 to ROTATION_270 are 0 to 3).
         * The accelerometer reports the reaction to gravity, so gravity is its negative.
         */
        fun of(ax: Float, ay: Float, az: Float, quarterTurns: Int): ScreenGravity {
            val dx = -ax / EARTH; val dy = -ay / EARTH
            val (sx, syUp) = when (quarterTurns and 3) {
                1 -> -dy to dx
                2 -> -dx to -dy
                3 -> dy to -dx
                else -> dx to dy
            }
            return ScreenGravity(sx, -syUp, min(1f, abs(az) / EARTH))
        }
    }
}
