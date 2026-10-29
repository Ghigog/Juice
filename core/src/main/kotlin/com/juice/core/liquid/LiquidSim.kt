package com.juice.core.liquid

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Juice liquid simulation.
 *
 * Clavet-style particle fluid ("Particle-based viscoelastic fluid simulation", 2005):
 * position based, one step per 60 Hz frame, so velocities are in units per frame.
 *
 * All positions are in dp, not pixels. The tuning below was done on a ~360 dp wide card,
 * so keep the sim in dp and scale to pixels only when drawing.
 *
 * Each particle also carries a froth amount (0 = juice, 1 = foam). Foam is lighter,
 * takes up about twice the space, moves in clumps and clings to the glass.
 */
class LiquidSim(width: Float, height: Float, private val random: Random = Random.Default) {

    class Params {
        var inset = 6f          // gap between glass and liquid
        var corner = 24f        // inner corner radius of the glass
        var h = 26f             // interaction radius
        var rho0 = 4.2f         // rest density
        var k = 0.35f           // pressure stiffness
        var kNear = 0.9f        // near-pressure stiffness (stops clumping)
        var xsph = 0.05f        // velocity smoothing (viscosity); higher feels thicker
        var damp = 0.006f       // global drag
        var negP = 0.8f         // cohesion limit; lower gives more splash and droplets
        var restV = 0.5f        // below this speed, extra damping kills shimmer
        var restDamp = 0.3f
        var floorDrag = 0.04f   // friction against the glass when the phone lies flat
        var gScale = 0.3f       // 1 g in dp per frame squared
        var foamRho = 0.5f      // foam rest density as a fraction of juice
        var foamMass = 0.18f
        var foamDrag = 0.05f
        var foamXsph = 0.4f
        var foamStick = 0.985f
        var foamWall = 15f      // how close to the glass foam starts clinging
    }

    val p = Params()
    var width = width; private set
    var height = height; private set
    var count = 0; private set

    val x = FloatArray(CAPACITY); val y = FloatArray(CAPACITY)
    val vx = FloatArray(CAPACITY); val vy = FloatArray(CAPACITY)
    val foam = FloatArray(CAPACITY)
    val rho = FloatArray(CAPACITY)      // density from the last step; low means surface
    val wall = FloatArray(CAPACITY)     // distance to the glass from the last step
    val floorGap = FloatArray(CAPACITY) // distance to the floor from the last step

    /**
     * A flat floor: the top of the solid body of juice under the particles. Particles stay where
     * x * floorNx + y * floorNy <= floorLevel ((floorNx, floorNy) is a unit vector along gravity). Off while infinite.
     */
    var floorNx = 0f; var floorNy = 1f; var floorLevel = Float.POSITIVE_INFINITY

    private val px = FloatArray(CAPACITY); private val py = FloatArray(CAPACITY)
    private val nvx = FloatArray(CAPACITY); private val nvy = FloatArray(CAPACITY)
    private val mass = FloatArray(CAPACITY)
    private val sorted = IntArray(CAPACITY); private val cellOf = IntArray(CAPACITY)
    private var cols = 0; private var rows = 0
    private var cellCount = IntArray(0); private var cellStart = IntArray(0); private var fillPos = IntArray(0)
    private var wnx = 0f; private var wny = 0f

    init { resizeGrid() }

    private fun resizeGrid() {
        cols = kotlin.math.ceil(width / p.h).toInt() + 1
        rows = kotlin.math.ceil(height / p.h).toInt() + 1
        cellCount = IntArray(cols * rows); cellStart = IntArray(cols * rows + 1); fillPos = IntArray(cols * rows)
    }

    fun resize(w: Float, h: Float) {
        val sx = w / width; val sy = h / height
        for (i in 0 until count) { x[i] *= sx; y[i] *= sy; px[i] *= sx; py[i] *= sy }
        width = w; height = h; resizeGrid()
    }

    /** Pours [n] particles in, as a slab at the bottom or a disc in the centre. */
    fun fill(n: Int, centre: Boolean) {
        count = min(n, CAPACITY)
        val sp = p.h * 0.42f
        var i = 0
        if (centre) {
            val cx = width / 2; val cy = height / 2
            var ring = 0
            while (i < count) {
                val r = ring * sp
                val m = if (ring == 0) 1 else (2 * Math.PI * r / sp).toInt()
                var k = 0
                while (k < m && i < count) {
                    val a = k.toDouble() / m * Math.PI * 2
                    x[i] = cx + (kotlin.math.cos(a) * r).toFloat(); y[i] = cy + (kotlin.math.sin(a) * r).toFloat()
                    k++; i++
                }
                ring++
            }
        } else {
            val perRow = max(1, ((width - 2 * p.inset - 4) / sp).toInt())
            // On the floor when it lies flat along the bottom, else on the glass.
            val bottom = if (floorNy > 0.999f) min(height - p.inset - 2, floorLevel - 2) else height - p.inset - 2
            while (i < count) {
                val row = i / perRow
                x[i] = p.inset + 2 + (i % perRow + 0.5f * (row % 2)) * sp
                y[i] = bottom - row * sp
                i++
            }
        }
        for (j in 0 until count) { foam[j] = 0f; rho[j] = 0f; px[j] = x[j]; py[j] = y[j]; vx[j] = 0f; vy[j] = 0f; bounds(j) }
    }

    private fun buildGrid() {
        cellCount.fill(0)
        for (i in 0 until count) {
            val cx = (x[i] / p.h).toInt().coerceIn(0, cols - 1)
            val cy = (y[i] / p.h).toInt().coerceIn(0, rows - 1)
            val c = cy * cols + cx
            cellOf[i] = c; cellCount[c]++
        }
        cellStart[0] = 0
        for (c in 0 until cols * rows) cellStart[c + 1] = cellStart[c] + cellCount[c]
        System.arraycopy(cellStart, 0, fillPos, 0, cols * rows)
        for (i in 0 until count) sorted[fillPos[cellOf[i]]++] = i
    }

    /** Keeps particle [i] inside the rounded glass. Returns its distance to the glass. */
    private fun bounds(i: Int): Float {
        val over = x[i] * floorNx + y[i] * floorNy - floorLevel
        if (over > 0f) { x[i] -= over * floorNx; y[i] -= over * floorNy }
        floorGap[i] = max(0f, -over)
        val l = p.inset; val t = p.inset; val r = width - p.inset; val b = height - p.inset; val cr = p.corner
        val cx = min(max(x[i], l + cr), r - cr); val cy = min(max(y[i], t + cr), b - cr)
        val dx = x[i] - cx; val dy = y[i] - cy; val d = sqrt(dx * dx + dy * dy)
        if (d > 1e-6f) { wnx = dx / d; wny = dy / d } else { wnx = 0f; wny = 0f }
        if (d > cr) { x[i] = cx + dx / d * cr; y[i] = cy + dy / d * cr; return 0f }
        return cr - d
    }

    /**
     * Advances one 60 Hz frame.
     * gx, gy: gravity in the screen plane, in g (screen axes, y down). gz: gravity into the screen, in g.
     * Finger: position and movement this frame, all in dp. Pass fr = 0 for no finger.
     * jitter: random fizz agitation.
     */
    fun step(gx: Float, gy: Float, gz: Float, fx: Float, fy: Float, fdx: Float, fdy: Float, fr: Float, jitter: Float) {
        val ax0 = gx * p.gScale; val ay0 = gy * p.gScale
        val h = p.h; val h2 = h * h
        val baseDrag = p.damp + p.floorDrag * kotlin.math.abs(gz)
        for (i in 0 until count) {
            mass[i] = 1 - (1 - p.foamMass) * foam[i]
            val drag = 1 - baseDrag - p.foamDrag * foam[i]
            // submerged foam floats up hard; foam already on top just gets lighter
            val gm = 1 - foam[i] * (if (rho[i] > p.rho0 * 0.78f) 2.2f else 0.5f)
            vx[i] = vx[i] * drag + ax0 * gm; vy[i] = vy[i] * drag + ay0 * gm
            if (jitter > 0f) { vx[i] += (random.nextFloat() - 0.5f) * jitter; vy[i] += (random.nextFloat() - 0.5f) * jitter }
            px[i] = x[i]; py[i] = y[i]
            x[i] += vx[i]; y[i] += vy[i]
        }
        if (fr > 0f) {
            val r2f = fr * fr
            for (i in 0 until count) {
                val dx = x[i] - fx; val dy = y[i] - fy; val d2 = dx * dx + dy * dy
                if (d2 < r2f) {
                    val d = max(sqrt(d2), 0.001f); val w = 1 - d / fr
                    x[i] += fdx * w * 0.9f; y[i] += fdy * w * 0.9f
                    val push = (fr - d) * 0.35f
                    x[i] += dx / d * push; y[i] += dy / d * push
                }
            }
        }
        buildGrid()

        // double density relaxation
        for (i in 0 until count) {
            val ci = cellOf[i]; val gcx = ci % cols; val gcy = ci / cols
            var rh = 0f; var rhN = 0f
            for (oy in -1..1) { val yy = gcy + oy; if (yy < 0 || yy >= rows) continue
                for (ox in -1..1) { val xx = gcx + ox; if (xx < 0 || xx >= cols) continue
                    val c = yy * cols + xx
                    for (s in cellStart[c] until cellStart[c + 1]) { val j = sorted[s]; if (j == i) continue
                        val dx = x[j] - x[i]; val dy = y[j] - y[i]; val r2 = dx * dx + dy * dy
                        if (r2 < h2) { val q = 1 - sqrt(r2) / h; rh += q * q; rhN += q * q * q }
                    }
                }
            }
            rho[i] = rh
            val r0 = p.rho0 * (1 - (1 - p.foamRho) * foam[i])
            val pr = max(p.k * (rh - r0), -p.k * (p.negP + 2 * foam[i]))
            val prN = p.kNear * rhN
            val mi = mass[i]
            var ax = 0f; var ay = 0f
            for (oy in -1..1) { val yy = gcy + oy; if (yy < 0 || yy >= rows) continue
                for (ox in -1..1) { val xx = gcx + ox; if (xx < 0 || xx >= cols) continue
                    val c = yy * cols + xx
                    for (s in cellStart[c] until cellStart[c + 1]) { val j = sorted[s]; if (j == i) continue
                        val dx = x[j] - x[i]; val dy = y[j] - y[i]; val r2 = dx * dx + dy * dy
                        if (r2 < h2 && r2 > 1e-8f) {
                            val r = sqrt(r2); val q = 1 - r / h
                            val d = pr * q + prN * q * q
                            val inv = 1 / (mi + mass[j])
                            val ux = dx / r * d; val uy = dy / r * d
                            val wj = mi * inv; val wi = mass[j] * inv
                            x[j] += ux * wj; y[j] += uy * wj; ax -= ux * wi; ay -= uy * wi
                        }
                    }
                }
            }
            x[i] += ax; y[i] += ay
        }

        // glass, foam cling, and velocity from movement
        for (i in 0 until count) {
            val wd = bounds(i); wall[i] = wd
            if (foam[i] > 0.15f && wd < p.foamWall) {
                // damp only motion along the glass, so foam can still float off the floor
                val st = p.foamStick * min(1f, foam[i] * 1.4f) * sqrt(1 - wd / p.foamWall)
                val mx = x[i] - px[i]; val my = y[i] - py[i]; val mn = mx * wnx + my * wny
                val tx = mx - mn * wnx; val ty = my - mn * wny
                x[i] -= tx * st; y[i] -= ty * st
                // clinging can drag foam back past a floor that moved this frame
                val over = x[i] * floorNx + y[i] * floorNy - floorLevel
                if (over > 0f) { x[i] -= over * floorNx; y[i] -= over * floorNy }
            }
            vx[i] = x[i] - px[i]; vy[i] = y[i] - py[i]
        }

        // XSPH smoothing (cheap viscosity), stronger for foam
        for (i in 0 until count) {
            val ci = cellOf[i]; val gcx = ci % cols; val gcy = ci / cols
            var sx = 0f; var sy = 0f; var sw = 0f
            for (oy in -1..1) { val yy = gcy + oy; if (yy < 0 || yy >= rows) continue
                for (ox in -1..1) { val xx = gcx + ox; if (xx < 0 || xx >= cols) continue
                    val c = yy * cols + xx
                    for (s in cellStart[c] until cellStart[c + 1]) { val j = sorted[s]; if (j == i) continue
                        val dx = x[j] - x[i]; val dy = y[j] - y[i]; val r2 = dx * dx + dy * dy
                        if (r2 < h2) { val q = 1 - sqrt(r2) / h; sx += (vx[j] - vx[i]) * q; sy += (vy[j] - vy[i]) * q; sw += q }
                    }
                }
            }
            val c2 = p.xsph + p.foamXsph * foam[i]
            if (sw > 0f) { nvx[i] = vx[i] + c2 * sx / max(1f, sw); nvy[i] = vy[i] + c2 * sy / max(1f, sw) }
            else { nvx[i] = vx[i]; nvy[i] = vy[i] }
        }
        val rv2 = p.restV * p.restV
        for (i in 0 until count) {
            vx[i] = nvx[i]; vy[i] = nvy[i]
            val s2 = vx[i] * vx[i] + vy[i] * vy[i]
            if (s2 < rv2) { val f = 1 - p.restDamp * (1 - sqrt(s2) / p.restV); vx[i] *= f; vy[i] *= f }
        }
    }

    /** Adds one particle at ([px0], [py0]) moving at ([v0x], [v0y]) dp per frame. Returns false when full. */
    fun add(px0: Float, py0: Float, v0x: Float, v0y: Float): Boolean {
        if (count >= CAPACITY) return false
        val i = count++
        x[i] = px0; y[i] = py0; px[i] = px0; py[i] = py0; vx[i] = v0x; vy[i] = v0y
        foam[i] = 0f; rho[i] = 0f; wall[i] = 0f
        bounds(i)
        return true
    }

    /** Removes the [n] particles highest against gravity ([gx], [gy]): the liquid drains from its surface. */
    fun removeTop(n: Int, gx: Float, gy: Float) {
        repeat(min(n, count)) {
            var top = 0; var best = Float.MAX_VALUE
            for (i in 0 until count) { val d = x[i] * gx + y[i] * gy; if (d < best) { best = d; top = i } }
            val last = --count
            x[top] = x[last]; y[top] = y[last]; px[top] = px[last]; py[top] = py[last]
            vx[top] = vx[last]; vy[top] = vy[last]; foam[top] = foam[last]; rho[top] = rho[last]; wall[top] = wall[last]
        }
    }

    /** Adds ([dvx], [dvy]) dp per frame to every particle, e.g. a slosh. */
    fun impulse(dvx: Float, dvy: Float) { for (i in 0 until count) { vx[i] += dvx; vy[i] += dvy } }

    /** Number of particles within [rad] of a point. Uses the grid from the last step. */
    fun near(qx: Float, qy: Float, rad: Float): Int = neighbours(qx, qy, rad, null)

    /** Collects particles within [rad] of a point into [out] (if given). Returns how many. */
    fun neighbours(qx: Float, qy: Float, rad: Float, out: IntArray?): Int {
        val cx = (qx / p.h).toInt(); val cy = (qy / p.h).toInt(); val r2 = rad * rad
        var n = 0
        for (oy in -1..1) { val yy = cy + oy; if (yy < 0 || yy >= rows) continue
            for (ox in -1..1) { val xx = cx + ox; if (xx < 0 || xx >= cols) continue
                val c = yy * cols + xx
                for (s in cellStart[c] until cellStart[c + 1]) {
                    val j = sorted[s]; val dx = x[j] - qx; val dy = y[j] - qy
                    if (dx * dx + dy * dy < r2) { if (out != null) out[n] = j; n++ }
                }
            }
        }
        return n
    }

    companion object { const val CAPACITY = 2000 }
}
