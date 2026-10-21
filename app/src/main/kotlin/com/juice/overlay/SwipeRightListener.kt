package com.juice.overlay

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Decides when a touch has travelled right past [slop], or was a tap (lifted without moving past
 * [tapSlop]); fires at most once per touch.
 */
internal class SwipeRightDetector(private val onSwipeRight: () -> Unit) {
    private var downX = 0f
    private var fired = false
    private var moved = false

    fun down(x: Float) {
        downX = x
        fired = false
        moved = false
    }

    fun move(x: Float, slop: Float, tapSlop: Float = slop) {
        if (kotlin.math.abs(x - downX) > tapSlop) moved = true
        if (!fired && x - downX > slop) {
            fired = true
            onSwipeRight()
        }
    }

    fun up() {
        if (!fired && !moved) {
            fired = true
            onSwipeRight()
        }
    }
}

/** Fires [onSwipeRight] once a touch on the strip travels right past the touch slop, or taps it. */
internal class SwipeRightListener(onSwipeRight: () -> Unit) : View.OnTouchListener {
    private val detector = SwipeRightDetector(onSwipeRight)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> detector.down(event.rawX)
            MotionEvent.ACTION_MOVE -> detector.move(event.rawX, view.touchSlop(), view.touchSlop() / 4f)
            MotionEvent.ACTION_UP -> {
                detector.up()
                view.performClick()
            }
        }
        return true
    }

    private fun View.touchSlop() = ViewConfiguration.get(context).scaledTouchSlop * 4f
}
