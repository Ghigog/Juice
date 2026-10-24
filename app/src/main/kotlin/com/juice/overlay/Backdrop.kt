package com.juice.overlay

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

/**
 * The swipe-open hand-over, in order: the slice flies to the backdrop, then the overlay is
 * shown, then the stage is dismissed once the overlay has drawn its first frame. A throwing
 * [show] stops the hand-over there; the caller's guard cleans up.
 */
internal fun handOverToOverlay(
    fly: (onArrived: () -> Unit) -> Unit,
    show: () -> Unit,
    whenOverlayDrawn: (action: () -> Unit) -> Unit,
    dismissStage: () -> Unit,
) {
    fly {
        show()
        whenOverlayDrawn(dismissStage)
    }
}
