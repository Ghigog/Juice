package com.juice.core.physics

/**
 * Coarse 2D ripple grid ([WIDTH] x [HEIGHT] cells, row-major in [heights]) with values clamped to -1..1.
 * Positions are normalised 0..1. Arrays are allocated once; [step] does not allocate.
 */
class RippleField {
    private val h = FloatArray(WIDTH * HEIGHT)
    private val v = FloatArray(WIDTH * HEIGHT)

    /** Read-only view for the renderer, index `y * WIDTH + x`; do not modify. */
    val heights: FloatArray get() = h

    /** Pushes the cell under ([x], [y]) so a ring spreads from it. */
    fun disturb(x: Float, y: Float, strength: Float) {
        val cx = (x.coerceIn(0f, 1f) * (WIDTH - 1) + 0.5f).toInt()
        val cy = (y.coerceIn(0f, 1f) * (HEIGHT - 1) + 0.5f).toInt()
        val i = cy * WIDTH + cx
        h[i] = (h[i] + strength).coerceIn(-1f, 1f)
    }

    /** Disturbs every cell along the segment from ([x0], [y0]) to ([x1], [y1]), e.g. a finger or object wake. */
    fun wake(x0: Float, y0: Float, x1: Float, y1: Float, strength: Float) {
        val n = (maxOf(kotlin.math.abs(x1 - x0) * WIDTH, kotlin.math.abs(y1 - y0) * HEIGHT)).toInt() + 1
        for (k in 0..n) {
            val t = k.toFloat() / n
            disturb(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, strength)
        }
    }

    fun step(dt: Float) {
        val capped = dt.coerceIn(0f, MAX_DT)
        val steps = (capped / SUB_DT).toInt() + 1
        val sub = capped / steps
        repeat(steps) {
            for (y in 0 until HEIGHT) {
                for (x in 0 until WIDTH) {
                    val i = y * WIDTH + x
                    val l = h[if (x == 0) i + 1 else i - 1]
                    val r = h[if (x == WIDTH - 1) i - 1 else i + 1]
                    val u = h[if (y == 0) i + WIDTH else i - WIDTH]
                    val d = h[if (y == HEIGHT - 1) i - WIDTH else i + WIDTH]
                    v[i] += (SPEED_SQ * (l + r + u + d - 4f * h[i]) - RESTORE * h[i] - DAMPING * v[i]) * sub
                }
            }
            for (i in h.indices) h[i] = (h[i] + v[i] * sub).coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val WIDTH = 24
        const val HEIGHT = 16
        const val MAX_DT = 1f / 30
        private const val SUB_DT = 1f / 120
        private const val SPEED_SQ = 100f
        private const val DAMPING = 3f
        private const val RESTORE = 10f
    }
}
