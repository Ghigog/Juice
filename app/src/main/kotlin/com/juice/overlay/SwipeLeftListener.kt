package com.juice.overlay

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Decides when a touch has travelled left past [slop]; fires at most once per touch. */
internal class SwipeLeftDetector(private val onSwipeLeft: () -> Unit) {
    private var downX = 0f
    private var fired = false

    fun down(x: Float) {
        downX = x
        fired = false
    }

    fun move(x: Float, slop: Float) {
        if (!fired && downX - x > slop) {
            fired = true
            onSwipeLeft()
        }
    }
}

/** Fires [onSwipeLeft] once a touch on the strip travels left past the touch slop. */
internal class SwipeLeftListener(onSwipeLeft: () -> Unit) : View.OnTouchListener {
    private val detector = SwipeLeftDetector(onSwipeLeft)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> detector.down(event.rawX)
            MotionEvent.ACTION_MOVE -> detector.move(event.rawX, view.touchSlop())
        }
        return true
    }

    private fun View.touchSlop() = ViewConfiguration.get(context).scaledTouchSlop * 4f
}
