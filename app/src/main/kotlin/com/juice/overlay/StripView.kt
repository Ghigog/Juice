package com.juice.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** The trigger strip: a translucent pill with a handle bar, like the gesture bar it sits beside. */
internal class StripView(context: Context) : View(context) {
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(51, 255, 255, 255) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(191, 255, 255, 255) }

    init {
        contentDescription = context.getString(com.juice.R.string.strip_open)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, pill)
        val d = resources.displayMetrics.density
        val barWidth = (width * 0.42f).coerceAtMost(49 * d)
        val barHeight = 4 * d
        val left = (width - barWidth) / 2
        val top = (h - barHeight) / 2
        canvas.drawRoundRect(left, top, left + barWidth, top + barHeight, barHeight / 2, barHeight / 2, bar)
    }

    override fun performClick(): Boolean = super.performClick()
}
