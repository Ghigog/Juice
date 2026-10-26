package com.juice.overlay

import kotlin.math.roundToInt

/** What sits behind the overlay content: the blurred screen, or a dark semi-opaque scrim. */
internal enum class Backdrop {
    BLUR,
    SCRIM,
    ;

    companion object {
        /** Blur only when the user enabled it, the device supports it and battery saver is off. */
        fun choose(blurEnabled: Boolean, blurSupported: Boolean, batterySaver: Boolean): Backdrop =
            if (blurEnabled && blurSupported && !batterySaver) BLUR else SCRIM
    }
}

/** Where the slice is drawn on the stage: centre ([x], [y]) and [size], in screen px. */
internal data class SliceSpot(val x: Float, val y: Float, val size: Float) {
    /** The spot [t] of the way (0..1) from this one to [to]. */
    fun lerp(to: SliceSpot, t: Float) =
        SliceSpot(x + (to.x - x) * t, y + (to.y - y) * t, size + (to.size - size) * t)
}

/**
 * Where the overlay's backdrop slice sits: top-right, 280dp, its centre 70dp past the corner.
 * Must match SliceBackdrop in OverlayShell.kt (BACKDROP_DP and its quarter-size offset).
 */
internal fun backdropSliceSpot(screenWidthPx: Int, density: Float): SliceSpot {
    val size = 280f * density
    val past = 70f * density
    return SliceSpot(screenWidthPx - size / 2 + past, size / 2 - past, size)
}

// About 40dp on a typical phone; 40px was a faint smear that the dark tint swallowed.
internal const val BLUR_RADIUS_PX = 120

/** The window blur radius for a reveal [progress] (0..1): the window blur cannot be clipped to the growing ground, so it ramps instead. */
internal fun blurRadiusFor(progress: Float): Int = (BLUR_RADIUS_PX * progress.coerceIn(0f, 1f)).roundToInt()

/**
 * The swipe-open hand-over: the overlay is shown as the slice starts flying to the backdrop, so
 * the ground grows while it flies. The stage is dismissed once the flight is done and the overlay
 * has drawn its first frame. A throwing [show] stops the hand-over before the flight starts; the
 * caller's guard cleans up.
 */
internal fun handOverToOverlay(
    fly: (onArrived: () -> Unit) -> Unit,
    show: () -> Unit,
    whenOverlayDrawn: (action: () -> Unit) -> Unit,
    dismissStage: () -> Unit,
) {
    var pending = 2
    val oneDone = { if (--pending == 0) dismissStage() }
    show()
    whenOverlayDrawn(oneDone)
    fly(oneDone)
}

/**
 * The close hand-over, the open played backwards: when the ground starts to drain ([draining]) the
 * slice flies from the backdrop to the handle; once the drain is done ([drained]) and the slice
 * has landed, [finish] removes the overlay and the handle takes touches. A drain that never
 * started (reduced motion) finishes at once without a flight.
 */
internal class HandBack(
    private val fly: (onLanded: () -> Unit) -> Unit,
    private val finish: () -> Unit,
) {
    private var pending = 0
    private var flying = false

    fun draining() {
        if (flying) return
        flying = true
        pending = 2
        fly(::oneDone)
    }

    fun drained() {
        if (flying) oneDone() else finish()
    }

    private fun oneDone() {
        if (--pending == 0) finish()
    }
}
