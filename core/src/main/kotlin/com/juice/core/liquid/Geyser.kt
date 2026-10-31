package com.juice.core.liquid

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The reward for a full meter: shake the phone to build pressure in the juice, and at full pressure it
 * spurts out of the top of the card as a spray of drops. Once the pressure has vented the juice is still
 * there, so it can be shaken again for as long as the meter stays full.
 *
 * Call [step] once per 60 Hz frame with the shake reading ([Froth.shake], 0 to 3). Drops are in dp from
 * the card's top-left corner, y down; negative y is above the card. Pure Kotlin so it can be unit tested.
 */
class Geyser(private val random: Random = Random.Default) {

    enum class Phase { IDLE, BUILDING, SPURTING, SETTLING }

    class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, val life: Int) { var age = 0 }

    val drops = ArrayList<Drop>()

    /** 0 to 1. Rises while shaken, leaks away when still, vents through the spurt. */
    var pressure = 0f; private set
    var phase = Phase.IDLE; private set
    private var cooldown = 0

    /** True only while the meter is full. Turning it off lets the pressure out quietly; airborne drops still land. */
    var armed = false
        set(value) {
            field = value
            if (!value && phase != Phase.SETTLING) { pressure = 0f; phase = if (drops.isEmpty()) Phase.IDLE else Phase.SETTLING; cooldown = 0 }
        }

    /** Whether to show the big SHAKE prompt: armed and not mid-spurt. */
    val prompting: Boolean get() = armed && (phase == Phase.IDLE || phase == Phase.BUILDING)

    /** Whether [step] still has anything to do. */
    val busy: Boolean get() = armed || phase != Phase.IDLE || drops.isNotEmpty()

    /** Advances one frame on a card [widthDp] wide. Returns true on the frame the juice bursts. */
    fun step(shake: Float, widthDp: Float): Boolean {
        var burst = false
        when (phase) {
            Phase.IDLE, Phase.BUILDING -> if (armed) {
                val push = max(0f, shake - SHAKE_MIN)
                pressure = if (push > 0f) min(1f, pressure + push * BUILD) else max(0f, pressure - LEAK)
                phase = if (pressure > 0f) Phase.BUILDING else Phase.IDLE
                if (pressure >= 1f) { phase = Phase.SPURTING; burst = true }
            }
            Phase.SPURTING -> {
                spray(widthDp)
                // Shaking through the spurt keeps it going a little longer.
                pressure = min(1f, pressure - VENT + max(0f, shake - SHAKE_MIN) * BUILD * 0.3f)
                if (pressure <= 0f) { pressure = 0f; phase = Phase.SETTLING; cooldown = COOLDOWN_FRAMES }
            }
            Phase.SETTLING -> if (cooldown > 0) cooldown-- else if (drops.isEmpty()) phase = Phase.IDLE
        }
        moveDrops()
        return burst
    }

    private fun spray(widthDp: Float) {
        val n = (pressure * DROPS_PER_FRAME + random.nextFloat()).toInt()
        repeat(n) {
            if (drops.size >= MAX_DROPS) return
            val spread = (random.nextFloat() + random.nextFloat() - 1f) // -1..1, peaked at the middle
            val lift = (0.55f + pressure * 0.6f) * (5f + random.nextFloat() * 5f)
            drops.add(Drop(
                x = widthDp / 2 + spread * widthDp * 0.18f, y = 0f,
                vx = spread * 2.2f + (random.nextFloat() - 0.5f) * 1.2f, vy = -lift,
                r = 1.5f + random.nextFloat() * random.nextFloat() * 5f,
                life = 50 + random.nextInt(50),
            ))
        }
    }

    private fun moveDrops() {
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.age++
            d.vx *= 0.99f; d.vy += GRAVITY
            d.x += d.vx; d.y += d.vy
            // Gone once it falls back into the card, or fades out.
            if (d.age > d.life || (d.vy > 0f && d.y > 0f)) it.remove()
        }
    }

    companion object {
        /** Shake readings below this do not build pressure; it filters out a hand's tremble. */
        const val SHAKE_MIN = 0.5f
        /** Pressure per frame per unit of shake above [SHAKE_MIN]: about two seconds of hard shaking fills it. */
        const val BUILD = 0.0035f
        const val LEAK = 0.003f
        /** Pressure lost per frame while spurting: about two seconds of spray. */
        const val VENT = 0.008f
        const val COOLDOWN_FRAMES = 90
        const val DROPS_PER_FRAME = 7f
        const val MAX_DROPS = 400
        /** dp per frame squared. */
        const val GRAVITY = 0.3f
    }
}
