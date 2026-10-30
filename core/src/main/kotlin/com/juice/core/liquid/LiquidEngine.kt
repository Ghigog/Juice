package com.juice.core.liquid

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Ties the sim, froth, input and sleeping together. Call [tick] at 60 Hz.
 * Everything is in dp. Pure Kotlin, no Android dependencies.
 *
 * The Thirst Meter sets how much juice there is through [fill] and [fillTo]; a full meter fills the glass.
 * New particles stream in from the side opposite gravity, a few a frame; drained ones leave from the surface.
 *
 * Shallow juice comes to rest and sleeps on its own. Deep juice keeps churning slowly inside (one density
 * pass a frame cannot hold up a tall column), so after [IDLE_FRAMES] with no tilt, touch, kick or pour the
 * engine sleeps anyway and the juice holds still until something moves it.
 */
class LiquidEngine(widthDp: Float, heightDp: Float, private val random: Random = Random.Default) {
    val sim = LiquidSim(widthDp, heightDp, random)
    val froth = Froth(sim, random)

    /** Gravity in g, screen axes (x right, y down). z is into the screen. Set from the accelerometer. */
    var gx = 0f; private set
    var gy = 1f; private set
    var gz = 0f; private set
    private var rawGx = 0f; private var rawGy = 1f; private var rawGz = 0f

    /** Reduce motion turns this off: gravity stays straight down and touches, kicks and shakes do nothing. */
    var motion = true
        set(value) { if (value == field) return; field = value; applyGravity(); if (!value) fingerDown = false; wake() }

    /** Foam, bubbles and pops. Off clears them at once. */
    var frothEnabled = true
        set(value) { if (value == field) return; field = value; if (!value) froth.reset() }

    var sleeping = false; private set
    private var calmFrames = 0
    private var idleFrames = 0
    private var inputGx = 0f; private var inputGy = 1f
    private var sleepGx = 0f; private var sleepGy = 1f

    private var fingerDown = false
    private var fx = 0f; private var fy = 0f; private var ftx = 0f; private var fty = 0f
    var fingerRadius = 32f

    /** Particles still to stream in. */
    var pending = 0; private set

    fun setGravity(x: Float, y: Float, z: Float) { rawGx = x; rawGy = y; rawGz = z; applyGravity() }

    private fun applyGravity() {
        if (motion) { gx = rawGx; gy = rawGy; gz = rawGz } else { gx = 0f; gy = 1f; gz = 0f }
    }

    /** True if the current gravity would wake a sleeping sim. Use it to restart your frame loop. */
    fun wantsToWake() = sleeping && hypot(gx - sleepGx, gy - sleepGy) > 0.04f

    /** Fills the glass with [particles] at once, already resting (a slab on the low side, or a disc when lying flat). */
    fun pour(particles: Int) {
        val flat = hypot(gx, gy) < 0.12f
        sim.fill(particles, centre = flat); froth.reset(); pending = 0; wake()
    }

    /** Fills the glass to [fill] (0 to 1) at once. */
    fun fill(fill: Float) = pour(particlesFor(fill, sim.width, sim.height))

    /** Heads for [fill] (0 to 1): juice streams in or drains from the surface. */
    fun fillTo(fill: Float) = pourTo(particlesFor(fill, sim.width, sim.height))

    /** Streams juice in, or drains it from the surface, until there are [particles]. */
    fun pourTo(particles: Int) {
        val target = particles.coerceIn(0, LiquidSim.CAPACITY)
        if (target < sim.count) {
            val (nx, ny) = down()
            sim.removeTop(sim.count - target, nx, ny)
            pending = 0
        } else pending = target - sim.count
        wake()
    }

    /** The juice it is heading for: what is in the glass plus what is still streaming in. */
    val target: Int get() = sim.count + pending

    fun resize(wDp: Float, hDp: Float) { sim.resize(wDp, hDp); wake() }

    fun touchDown(x: Float, y: Float) {
        if (!motion) return
        fingerDown = true; fx = x; fy = y; ftx = x; fty = y; wake()
    }
    fun touchMove(x: Float, y: Float) { ftx = x; fty = y }
    fun touchUp() { fingerDown = false }

    /** Sloshes the juice sideways (across gravity) by [velocity]; positive is to the right when upright. */
    fun kick(velocity: Float) {
        if (!motion || velocity == 0f) return
        val (nx, ny) = down()
        sim.impulse(ny * velocity * KICK_SCALE, -nx * velocity * KICK_SCALE)
        if (frothEnabled) froth.agitate(abs(velocity) * KICK_FROTH)
        wake()
    }

    /** Stirs up a burst of foam and bubbles, e.g. on reaching Satiated. */
    fun splash(amount: Float) {
        if (!frothEnabled) return
        froth.agitate(amount); wake()
    }

    fun wake() { sleeping = false; calmFrames = 0; idleFrames = 0 }

    /** Advances one 60 Hz frame. Returns false while asleep (nothing to draw). */
    fun tick(): Boolean {
        froth.sense(gx, gy)
        if (sleeping) {
            if (wantsToWake() || fingerDown || froth.agitation > 0.02f || pending > 0) wake() else return false
        }
        if (hypot(gx - inputGx, gy - inputGy) > 0.04f || fingerDown || pending > 0) {
            inputGx = gx; inputGy = gy; idleFrames = 0
        } else idleFrames++
        stream()
        var fdx = 0f; var fdy = 0f; var fr = 0f
        if (fingerDown) {
            fdx = ftx - fx; fdy = fty - fy; fr = fingerRadius
            if (frothEnabled) froth.fingerSpeed(hypot(fdx, fdy))
            fx = ftx; fy = fty
        }
        sim.step(gx, gy, gz, fx, fy, fdx, fdy, fr, if (frothEnabled) froth.jitter else 0f)
        if (frothEnabled) froth.afterStep(gx, gy)

        var sum = 0f
        for (i in 0 until sim.count) sum += abs(sim.vx[i]) + abs(sim.vy[i])
        val mean = if (sim.count > 0) sum / sim.count else 0f
        val frothing = frothEnabled && froth.busy
        if (mean < 0.12f && !fingerDown && !frothing && pending == 0) calmFrames++ else calmFrames = 0
        val idle = idleFrames > IDLE_FRAMES && froth.agitation < 0.02f
        if (calmFrames > 60 || idle) { sleeping = true; sleepGx = gx; sleepGy = gy }
        return true
    }

    /** Pours up to [POUR_PER_FRAME] particles in from the side opposite gravity, falling along it. */
    private fun stream() {
        if (pending == 0) return
        val (nx, ny) = down()
        val inset = sim.p.inset + sim.p.h * 0.5f
        val halfW = sim.width / 2 - inset; val halfH = sim.height / 2 - inset
        if (halfW <= 0f || halfH <= 0f) { pending = 0; return }
        // From the centre, back along gravity to just inside the glass.
        val reach = min(if (abs(nx) > 1e-3f) halfW / abs(nx) else Float.MAX_VALUE, if (abs(ny) > 1e-3f) halfH / abs(ny) else Float.MAX_VALUE)
        val sx = sim.width / 2 - nx * reach * 0.9f; val sy = sim.height / 2 - ny * reach * 0.9f
        repeat(min(pending, POUR_PER_FRAME)) {
            val across = (random.nextFloat() - 0.5f) * POUR_WIDTH
            if (!sim.add(sx + ny * across, sy - nx * across, nx * POUR_SPEED, ny * POUR_SPEED)) { pending = 0; return }
            pending--
        }
    }

    /** Unit vector along gravity; straight down when the phone lies flat. */
    private fun down(): Pair<Float, Float> {
        val m = hypot(gx, gy)
        return if (m < 0.12f) 0f to 1f else gx / m to gy / m
    }

    val fingerX get() = fx
    val fingerY get() = fy
    val isTouching get() = fingerDown

    companion object {
        /** Particles that fill a 340 x 440 dp card; scale by area for others. */
        const val FULL_PARTICLES = 1800
        /** Three seconds with nothing moving the juice, then it holds still even if it has not settled. */
        const val IDLE_FRAMES = 180
        const val REFERENCE_AREA = 340f * 440f
        const val POUR_PER_FRAME = 3
        const val POUR_WIDTH = 22f
        const val POUR_SPEED = 2.5f
        const val KICK_SCALE = 0.5f
        const val KICK_FROTH = 0.04f

        /** How many particles hold [fill] (0 to 1) of a [widthDp] x [heightDp] glass. */
        fun particlesFor(fill: Float, widthDp: Float, heightDp: Float): Int {
            val full = FULL_PARTICLES * (widthDp * heightDp) / REFERENCE_AREA
            return (full * fill.coerceIn(0f, 1f)).roundToInt().coerceIn(0, LiquidSim.CAPACITY)
        }
    }
}
