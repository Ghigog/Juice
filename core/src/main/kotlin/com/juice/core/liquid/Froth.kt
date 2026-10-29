package com.juice.core.liquid

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shake detection, foam, rising bubbles and pops.
 * Pure Kotlin so it can be unit tested; drawing lives in the app's JuiceLiquidView.
 */
class Froth(private val sim: LiquidSim, private val random: Random = Random.Default) {

    class Bubble(
        val host: Int, var ox: Float, var oy: Float, val r: Float, val life: Float, val ph: Float,
    ) { var vx = 0f; var vy = 0f; var age = 0; var ang = 0f; var stretch = 0f }

    class Pop(val x: Float, val y: Float, val r: Float, val dir: Float) { var t = 0 }

    val bubbles = ArrayList<Bubble>()
    val pops = ArrayList<Pop>()

    /** 0 to 3. Rises with sudden changes in tilt (shakes) and fast finger swipes, decays on its own. */
    var agitation = 0f; private set
    /** Particles that are currently at least a little foamy. */
    var foamLive = 0; private set

    private var lpx = 0f; private var lpy = 1f
    private val nb = IntArray(LiquidSim.CAPACITY)

    val jitter: Float get() = min(agitation, 2f) * 0.1f
    val busy: Boolean get() = agitation >= 0.02f || bubbles.isNotEmpty() || pops.isNotEmpty() || foamLive > 0

    /** Carbonated flavours grow bubbles when shaken; still ones (Golden Honey) only foam. */
    var carbonated = true
    /** Chance per frame of a bubble rising on its own, for a drink that keeps fizzing (Satiated). */
    var sparkle = 0f

    /** Clears every bubble, pop and bit of foam. */
    fun reset() {
        bubbles.clear(); pops.clear(); agitation = 0f; foamLive = 0
        sim.foam.fill(0f)
    }

    /** Stirs the drink by [amount] (agitation is 0 to 3), e.g. a burst of froth on reaching Satiated. */
    fun agitate(amount: Float) { agitation = min(3f, agitation + max(0f, amount)) }

    /** Call once per frame with the current gravity. A high-pass filter turns shakes into agitation. */
    fun sense(gx: Float, gy: Float) {
        val hx = gx - lpx; val hy = gy - lpy
        lpx += hx * 0.08f; lpy += hy * 0.08f
        val jerk = hypot(hx, hy)
        agitation = min(3f, agitation * 0.965f + max(0f, jerk - 0.35f) * 0.5f)
    }

    fun fingerSpeed(sp: Float) { agitation = min(3f, agitation + max(0f, sp - 14f) * 0.006f) }

    /** Call once per frame after LiquidSim.step. */
    fun afterStep(gx: Float, gy: Float) {
        updateFoam()
        val shaken = if (carbonated) (agitation * 2.2f + random.nextFloat() * 0.6f).toInt() else 0
        spawnBubbles(shaken + if (random.nextFloat() < sparkle) 1 else 0)
        updateBubbles(gx, gy)
    }

    private fun saturation() = if (sim.count == 0) 0f else max(0f, 1 - foamLive.toFloat() / sim.count / 0.42f)

    private fun updateFoam() {
        val f = sim.foam; val r0 = sim.p.rho0
        val make = agitation * saturation()
        val calm = max(0f, 1 - agitation * 0.8f)
        var live = 0
        for (i in 0 until sim.count) {
            val surface = sim.rho[i] < r0 * 0.8f && sim.wall[i] > 9f && sim.floorGap[i] > sim.p.h
            if (make > 0.05f && f[i] < 1f) {
                val sp = min(3f, hypot(sim.vx[i], sim.vy[i]))
                val p = make * (if (surface) 0.014f else 0.0015f) * (0.4f + sp * 0.35f)
                if (random.nextFloat() < p) f[i] = min(1f, f[i] + 0.45f + random.nextFloat() * 0.4f)
            }
            if (f[i] > 0f) {
                // drains back into juice, faster at the surface
                f[i] = max(0f, f[i] - (0.0016f + 0.0024f * (if (surface) 1f else 0.4f)) * calm)
                if (surface && f[i] > 0.15f && random.nextFloat() < 0.006f * calm) {
                    pops.add(Pop(sim.x[i] + (random.nextFloat() - 0.5f) * 10, sim.y[i] + (random.nextFloat() - 0.5f) * 10,
                        0.8f + random.nextFloat() * 1.6f, random.nextFloat() * 6))
                }
                if (f[i] > 0.01f) live++
            }
        }
        foamLive = live
    }

    private fun spawnBubbles(n: Int) {
        var k = 0
        while (k < n && bubbles.size < 170) {
            k++
            val host = (random.nextFloat() * sim.count).toInt()
            if (host >= sim.count || sim.foam[host] > 0.3f) continue
            val big = random.nextFloat()
            bubbles.add(Bubble(host, (random.nextFloat() - 0.5f) * 8, (random.nextFloat() - 0.5f) * 8,
                1.4f + big * big * 5.5f, 90 + random.nextFloat() * 160, random.nextFloat() * 6))
        }
    }

    private fun foamAround(x: Float, y: Float, rad: Float, amt: Float) {
        val n = sim.neighbours(x, y, rad, nb)
        for (k in 0 until n) { val j = nb[k]; sim.foam[j] = min(1f, sim.foam[j] + amt) }
    }

    private fun updateBubbles(gx: Float, gy: Float) {
        val gm = hypot(gx, gy)
        val w = sim.width; val h = sim.height
        val iter = bubbles.listIterator(bubbles.size)
        while (iter.hasPrevious()) {
            val b = iter.previous(); b.age++
            val lift = sim.p.gScale * (0.9f + b.r * 0.28f) // bigger bubbles rise faster
            val wob = sin(b.ph + b.age * (0.35f - b.r * 0.03f)) * 0.09f * min(1f, gm + 0.2f) * b.r * 0.4f
            b.vx = b.vx * 0.9f - gx * lift + (-gy) * wob
            b.vy = b.vy * 0.9f - gy * lift + gx * wob
            b.ox += b.vx; b.oy += b.vy
            val x = bubbleX(b); val y = bubbleY(b)
            val sp = hypot(b.vx, b.vy); b.ang = atan2(b.vy, b.vx); b.stretch = min(0.35f, sp * 0.12f)
            val gone = b.host >= sim.count
            val surfaced = !gone && (x < 4 || y < 4 || x > w - 4 || y > h - 4 ||
                sim.near(x, y, sim.p.h * 0.5f) < 1 || sim.foam[b.host] > 0.5f)
            if (gone || surfaced || b.age > b.life) {
                if (surfaced) foamAround(x, y, sim.p.h * (0.45f + b.r * 0.06f), (0.12f + b.r * 0.04f) * saturation())
                pops.add(Pop(x, y, b.r, random.nextFloat() * 6))
                iter.remove()
            }
        }
        val pi = pops.iterator()
        while (pi.hasNext()) { val p = pi.next(); if (++p.t > POP_FRAMES) pi.remove() }
    }

    fun bubbleX(b: Bubble) = sim.x[b.host] + b.ox
    fun bubbleY(b: Bubble) = sim.y[b.host] + b.oy

    companion object { const val POP_FRAMES = 14 }
}
