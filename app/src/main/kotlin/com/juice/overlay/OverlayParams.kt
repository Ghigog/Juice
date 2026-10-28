package com.juice.overlay

import com.juice.settings.JuiceSettings

/** How far in from each edge of the screen, in px, the slice handle's centre may sit. */
internal data class HandleEdges(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        /**
         * Keeps the handle's window ([margin] from its centre to its edge) on screen, and the
         * logo ([logoHalf] from its centre) clear of the system bars and cutout ([insetLeft] and the rest).
         */
        fun of(margin: Float, logoHalf: Float, insetLeft: Int, insetTop: Int, insetRight: Int, insetBottom: Int) = HandleEdges(
            maxOf(margin, insetLeft + logoHalf),
            maxOf(margin, insetTop + logoHalf),
            maxOf(margin, insetRight + logoHalf),
            maxOf(margin, insetBottom + logoHalf),
        )
    }
}

/** [value] kept between [low] and [high]; the middle of the two when there is no room between them. */
private fun Float.within(low: Float, high: Float): Float = if (low > high) (low + high) / 2 else coerceIn(low, high)

/**
 * The slice handle's centre in px on a [width] x [height] px screen, kept [edges] inside it. The
 * place is saved as fractions of the screen, so it follows the screen round when it rotates.
 */
internal fun JuiceSettings.handleCentre(width: Int, height: Int, edges: HandleEdges = HandleEdges(0f, 0f, 0f, 0f)): Pair<Float, Float> =
    (handleX.coerceIn(0f, 1f) * width).within(edges.left, width - edges.right) to
        (handleY.coerceIn(0f, 1f) * height).within(edges.top, height - edges.bottom)

/** Where a handle dropped at ([x], [y]) px lands, kept [edges] inside the screen. */
internal fun handleLanding(x: Float, y: Float, width: Int, height: Int, edges: HandleEdges): Pair<Float, Float> =
    x.within(edges.left, width - edges.right) to y.within(edges.top, height - edges.bottom)

/** Where a handle dropped at ([x], [y]) px is saved: fractions of the screen, kept [edges] inside it. */
internal fun handleFractions(x: Float, y: Float, width: Int, height: Int, edges: HandleEdges): Pair<Float, Float> {
    val (lx, ly) = handleLanding(x, y, width, height, edges)
    return lx / width to ly / height
}
