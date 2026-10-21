package com.juice.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.juice.feedback.FeedbackEvent
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What letting go of the handle does. */
internal enum class Release { NONE, OPEN, MOVED }

/**
 * Reads a press on the slice handle: travel past [moveSlop] then out past the swipe radius, any
 * way, opens Juice on release; holding still until [hold] turns the press into a move instead.
 */
internal class SliceGesture(private val moveSlop: Float) {
    var dx = 0f
        private set
    var dy = 0f
        private set
    var pressed = false
        private set
    var moving = false
        private set
    var ready = false
        private set
    private var downX = 0f
    private var downY = 0f
    private var travelled = false

    /** True while the press is a swipe: pressed and not turned into a move. */
    val swiping: Boolean get() = pressed && !moving

    fun down(x: Float, y: Float) {
        downX = x
        downY = y
        dx = 0f
        dy = 0f
        pressed = true
        moving = false
        travelled = false
        ready = false
    }

    /** The press was held: it becomes a move if it has not travelled yet. Returns true if it did. */
    fun hold(): Boolean {
        if (!pressed || moving || travelled) return false
        moving = true
        return true
    }

    /** Follows the press; returns true when it has just crossed [radius] on the way out. */
    fun move(x: Float, y: Float, radius: Float): Boolean {
        dx = x - downX
        dy = y - downY
        val distance = hypot(dx, dy)
        if (distance > moveSlop) travelled = true
        if (moving || !travelled) return false
        val was = ready
        ready = distance >= radius
        return ready && !was
    }

    /** How far out the swipe is, 0..1 of [radius]. */
    fun pull(radius: Float): Float = if (swiping && travelled) (hypot(dx, dy) / radius).coerceAtMost(1f) else 0f

    fun up(): Release {
        val release = when {
            !pressed -> Release.NONE
            moving -> Release.MOVED
            ready -> Release.OPEN
            else -> Release.NONE
        }
        pressed = false
        moving = false
        ready = false
        return release
    }

    fun cancel() {
        pressed = false
        moving = false
        ready = false
    }
}

/**
 * The floating slice handle, in its own small window. While pressed for a swipe the window grows
 * to show the swipe ring around it; a hold lets it be dragged anywhere and dropped.
 */
internal class SliceHandleView(
    context: Context,
    private val windowManager: WindowManager,
    private val radius: () -> Float,
    private val feedback: (FeedbackEvent) -> Unit,
    private val onMoved: (x: Float, y: Float) -> Unit,
    onOpen: () -> Unit,
) : View(context) {
    var style: SliceStyle = SliceStyle.of(com.juice.core.progression.Flavour.ELECTRIC_CITRUS)
        set(value) { field = value; invalidate() }
    var accent: Int = 0
        set(value) { field = value; invalidate() }
    var slices: Int = 0
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val logo = LOGO_DP * density
    private val painter = SliceLogoPainter()
    private val gesture = SliceGesture(MOVE_SLOP_DP * density)
    private var centreX = 0f
    private var centreY = 0f
    private var startX = 0f
    private var startY = 0f
    private val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Screen coordinates, the same ones the full-screen overlay draws its slice in.
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
    private val hold = Runnable {
        if (gesture.hold()) {
            place(moveSide())
            feedback(FeedbackEvent.CAPTURE)
            invalidate()
        }
    }

    init {
        contentDescription = context.getString(com.juice.R.string.strip_open)
        setOnClickListener { onOpen() }
    }

    /** Adds the handle's window, centred on ([x], [y]) screen px. */
    fun show(x: Float, y: Float) {
        centreX = x
        centreY = y
        place(idleSide())
        windowManager.addView(this, params)
    }

    /** Moves the handle to ([x], [y]) unless a press is under way. */
    fun moveTo(x: Float, y: Float) {
        if (gesture.pressed) return
        centreX = x
        centreY = y
        place(idleSide())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.down(event.rawX, event.rawY)
                startX = centreX
                startY = centreY
                place(swipeSide())
                postDelayed(hold, HOLD_MILLIS)
            }
            MotionEvent.ACTION_MOVE -> {
                val crossed = gesture.move(event.rawX, event.rawY, radius())
                if (gesture.moving) {
                    val bounds = windowManager.maximumWindowMetrics.bounds
                    val margin = EDGE_DP * density
                    centreX = (startX + gesture.dx).coerceIn(margin, bounds.width() - margin)
                    centreY = (startY + gesture.dy).coerceIn(margin, bounds.height() - margin)
                    place(moveSide())
                } else if (gesture.pull(radius()) > 0f) {
                    removeCallbacks(hold)
                }
                if (crossed) feedback(FeedbackEvent.SNAP)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                val release = gesture.up()
                place(idleSide())
                when (release) {
                    Release.OPEN -> performClick()
                    Release.MOVED -> {
                        feedback(FeedbackEvent.CAPTURE)
                        onMoved(centreX, centreY)
                    }
                    Release.NONE -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                gesture.cancel()
                centreX = startX
                centreY = startY
                place(idleSide())
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = radius()
        var x = cx
        var y = cy
        if (gesture.swiping) {
            painter.drawRing(canvas, cx, cy, r, gesture.pull(r), gesture.ready, accent, density)
            // The slice follows the finger out to the ring and no further.
            val d = hypot(gesture.dx, gesture.dy)
            val k = if (d > r) r / d else 1f
            if (gesture.pull(r) > 0f) {
                x += gesture.dx * k
                y += gesture.dy * k
            }
        }
        val scale = when {
            gesture.moving -> MOVE_SCALE
            gesture.pressed -> PRESS_SCALE
            else -> 1f
        }
        painter.draw(canvas, x, y, logo * scale, style, slices, active = 0)
    }

    private fun idleSide() = (logo + 2 * PAD_DP * density).roundToInt()

    private fun moveSide() = (logo * MOVE_SCALE + 2 * PAD_DP * density).roundToInt()

    private fun swipeSide() = maxOf(moveSide(), (2 * (radius() + PAD_DP * density)).roundToInt())

    private fun place(side: Int) {
        params.width = side
        params.height = side
        params.x = (centreX - side / 2f).roundToInt()
        params.y = (centreY - side / 2f).roundToInt()
        if (isAttachedToWindow) windowManager.updateViewLayout(this, params)
    }

    companion object {
        const val LOGO_DP = 56f
        private const val PAD_DP = 12f
        private const val EDGE_DP = 30f
        private const val MOVE_SLOP_DP = 6f
        private const val HOLD_MILLIS = 420L
        private const val PRESS_SCALE = 1.06f
        private const val MOVE_SCALE = 1.22f
    }
}
