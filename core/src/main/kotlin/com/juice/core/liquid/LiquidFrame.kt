package com.juice.core.liquid

import kotlin.math.hypot
import kotlin.math.min

/**
 * What one frame of juice looks like: a copy of the engine's particles, bubbles and pops, so the
 * engine can step on its own thread while the UI draws the last finished frame.
 */
class LiquidFrame {
    var count = 0; private set
    /** The sim's interaction radius, in dp; blob sizes scale with it. */
    var h = 0f; private set
    var froth = true; private set
    val x = FloatArray(LiquidSim.CAPACITY); val y = FloatArray(LiquidSim.CAPACITY)
    val speed = FloatArray(LiquidSim.CAPACITY); val foam = FloatArray(LiquidSim.CAPACITY)

    var bubbles = 0; private set
    val bubbleX = FloatArray(MAX_BUBBLES); val bubbleY = FloatArray(MAX_BUBBLES); val bubbleR = FloatArray(MAX_BUBBLES)
    val bubbleAge = FloatArray(MAX_BUBBLES); val bubblePh = FloatArray(MAX_BUBBLES)
    val bubbleAng = FloatArray(MAX_BUBBLES); val bubbleStretch = FloatArray(MAX_BUBBLES)

    var pops = 0; private set
    val popX = FloatArray(MAX_POPS); val popY = FloatArray(MAX_POPS); val popR = FloatArray(MAX_POPS)
    val popT = FloatArray(MAX_POPS); val popDir = FloatArray(MAX_POPS)

    fun copyFrom(e: LiquidEngine) {
        val sim = e.sim
        count = sim.count; h = sim.p.h; froth = e.frothEnabled
        System.arraycopy(sim.x, 0, x, 0, count); System.arraycopy(sim.y, 0, y, 0, count)
        System.arraycopy(sim.foam, 0, foam, 0, count)
        for (i in 0 until count) speed[i] = hypot(sim.vx[i], sim.vy[i])
        val fr = e.froth
        bubbles = min(fr.bubbles.size, MAX_BUBBLES)
        for (k in 0 until bubbles) {
            val b = fr.bubbles[k]
            bubbleX[k] = fr.bubbleX(b); bubbleY[k] = fr.bubbleY(b); bubbleR[k] = b.r
            bubbleAge[k] = b.age.toFloat(); bubblePh[k] = b.ph; bubbleAng[k] = b.ang; bubbleStretch[k] = b.stretch
        }
        pops = min(fr.pops.size, MAX_POPS)
        for (k in 0 until pops) {
            val p = fr.pops[k]
            popX[k] = p.x; popY[k] = p.y; popR[k] = p.r; popT[k] = p.t.toFloat(); popDir[k] = p.dir
        }
    }

    companion object {
        const val MAX_BUBBLES = 200
        const val MAX_POPS = 400
    }
}

/**
 * Three [LiquidFrame]s passed between the engine's thread and the UI's: the engine fills one and [publish]es it,
 * the UI takes the newest with [latest]. Neither ever waits on the other's work, only on a swap.
 */
class LiquidFrames {
    private var writing = LiquidFrame()
    private var ready = LiquidFrame()
    private var reading = LiquidFrame()
    private var fresh = false

    /** On the engine's thread: copies the engine's state and makes it the newest frame. */
    fun publish(e: LiquidEngine) {
        writing.copyFrom(e)
        synchronized(this) { val t = ready; ready = writing; writing = t; fresh = true }
    }

    /** On the UI thread: the newest published frame. It stays valid until the next call. */
    fun latest(): LiquidFrame = synchronized(this) {
        if (fresh) { val t = reading; reading = ready; ready = t; fresh = false }
        reading
    }
}
