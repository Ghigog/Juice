package com.juice.ui.overlay

import com.juice.core.gesture.GesturePoint

/**
 * Watches one stroke at a time and calls [onSwipeLeft] once, the moment the stroke first counts as a
 * left swipe (see [swipeOf]). It stays quiet until the next [down].
 */
internal class SwipeLeftListener(
    private val minDistance: Double,
    private val onSwipeLeft: () -> Unit,
) {
    private val stroke = mutableListOf<GesturePoint>()
    private var fired = false

    fun down(x: Double, y: Double) {
        stroke.clear()
        fired = false
        stroke += GesturePoint(x, y)
    }

    fun move(x: Double, y: Double) {
        if (fired) return
        stroke += GesturePoint(x, y)
        if (swipeOf(stroke, minDistance) == Swipe.LEFT) {
            fired = true
            onSwipeLeft()
        }
    }

    fun up(x: Double, y: Double) = move(x, y)
}
