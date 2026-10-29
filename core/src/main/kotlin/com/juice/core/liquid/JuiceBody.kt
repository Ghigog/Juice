package com.juice.core.liquid

import kotlin.math.abs

/**
 * The solid body of juice under the particles: the part of the [width] x [height] glass where
 * x * nx + y * ny >= level, for a unit vector (nx, ny) along gravity. Corners are left square; the view clips them.
 */
object JuiceBody {
    /** Corners of the body for this [level], as x0, y0, x1, y1, ...; empty when there is none. */
    fun polygon(nx: Float, ny: Float, level: Float, width: Float, height: Float): FloatArray {
        val xs = floatArrayOf(0f, width, width, 0f)
        val ys = floatArrayOf(0f, 0f, height, height)
        val out = ArrayList<Float>(10)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val da = xs[i] * nx + ys[i] * ny - level
            val db = xs[j] * nx + ys[j] * ny - level
            if (da >= 0f) { out += xs[i]; out += ys[i] }
            if ((da >= 0f) != (db >= 0f)) {
                val k = da / (da - db)
                out += xs[i] + (xs[j] - xs[i]) * k
                out += ys[i] + (ys[j] - ys[i]) * k
            }
        }
        return out.toFloatArray()
    }

    fun area(p: FloatArray): Float {
        var sum = 0f
        val n = p.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += p[2 * i] * p[2 * j + 1] - p[2 * j] * p[2 * i + 1]
        }
        return abs(sum) / 2f
    }

    /** The level at which the body covers [fraction] (0 to 1) of the glass. */
    fun level(nx: Float, ny: Float, fraction: Float, width: Float, height: Float): Float {
        val dots = floatArrayOf(0f, width * nx, height * ny, width * nx + height * ny)
        var lo = dots.min(); var hi = dots.max()
        val target = fraction.coerceIn(0f, 1f) * width * height
        repeat(32) {
            val mid = (lo + hi) / 2
            if (area(polygon(nx, ny, mid, width, height)) > target) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
}
