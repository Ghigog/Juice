package com.juice.ui.liquid

import android.util.Log
import com.juice.core.liquid.LiquidQuality

/**
 * Logs how long the juice takes, every few seconds while it moves, so a slow phone can be measured:
 * `adb logcat -s JuiceLiquid`. Steps are timed on the engine's thread, draws on the UI thread.
 */
internal class LiquidStats {
    @Volatile private var stepNanos = 0L
    @Volatile private var steps = 0
    @Volatile private var particles = 0
    private var drawNanos = 0L
    private var draws = 0
    private var since = 0L

    fun stepped(count: Int, nanos: Long, particles: Int) {
        stepNanos += nanos; steps += count; this.particles = particles
    }

    fun drew(nanos: Long, quality: LiquidQuality) {
        drawNanos += nanos; draws++
        if (since == 0L) since = System.nanoTime()
        if (System.nanoTime() - since < PERIOD_NANOS) return
        val step = if (steps > 0) stepNanos / steps / 1e6 else 0.0
        Log.d(TAG, "quality=$quality particles=$particles step=%.2f ms draw=%.2f ms (%d steps, %d draws)"
            .format(step, drawNanos / draws.coerceAtLeast(1) / 1e6, steps, draws))
        stepNanos = 0; steps = 0; drawNanos = 0; draws = 0; since = System.nanoTime()
    }

    private companion object {
        const val TAG = "JuiceLiquid"
        const val PERIOD_NANOS = 3_000_000_000L
    }
}
