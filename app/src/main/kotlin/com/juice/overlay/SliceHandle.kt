package com.juice.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.juice.feedback.FeedbackEvent
import com.juice.ui.overlay.REVEAL_MILLIS
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What letting go of the handle does. */
internal enum class Release { NONE, OPEN, MOVED, DISMISS }

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

    /** Lets go; a move released [overDismiss], over the dismiss target, is a [Release.DISMISS]. */
    fun up(overDismiss: Boolean = false): Release {
        val release = when {
            !pressed -> Release.NONE
            moving -> if (overDismiss) Release.DISMISS else Release.MOVED
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

/** True when the slice centre ([x], [y]) is within [radius] of the dismiss target's centre ([targetX], [targetY]). */
internal fun overDismissTarget(x: Float, y: Float, targetX: Float, targetY: Float, radius: Float): Boolean =
    hypot(x - targetX, y - targetY) <= radius

/**
 * The floating slice handle, in its own small window that never changes size, so the system
 * never has to nudge it back on screen. While pressed, the ring and the slice are drawn on a
 * full-screen [SliceStage] that ignores touches; the handle hands the slice over to it and back.
 */
internal class SliceHandleView(
    context: Context,
    private val windowManager: WindowManager,
    private val radius: () -> Float,
    private val reduceMotion: () -> Boolean,
    private val feedback: (FeedbackEvent) -> Unit,
    /** Saves a move dropped at ([x], [y]) and returns where the handle lands, clear of the system bars. */
    private val onMoved: (x: Float, y: Float) -> Pair<Float, Float>,
    private val onDismiss: () -> Unit,
    onOpen: () -> Unit,
) : View(context) {
    var style: SliceStyle = SliceStyle.of(com.juice.core.progression.Flavour.ELECTRIC_CITRUS)
        set(value) { field = value; invalidate(); stage.invalidate() }
    var accent: Int = 0
        set(value) { field = value; stage.invalidate() }
    var slices: Int = 0
        set(value) { field = value; invalidate(); stage.invalidate() }

    private val density = resources.displayMetrics.density
    private val logo = LOGO_DP * density
    private val side = (logo + 2 * PAD_DP * density).roundToInt()
    private val painter = SliceLogoPainter()
    private val gesture = SliceGesture(MOVE_SLOP_DP * density)
    private val stage = SliceStage(context)
    private var centreX = 0f
    private var centreY = 0f
    /** True while the stage draws the slice and this window draws nothing. */
    private var handedOff = false
    private var overTarget = false
    private var springBack: ValueAnimator? = null
    private var flight: ValueAnimator? = null
    private var windowAttached = false
    private var stageAttached = false
    private val params = WindowManager.LayoutParams(
        side, side,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Screen coordinates, the same ones the stage and the full-screen overlay draw in.
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
    private val hold = Runnable {
        if (gesture.hold()) {
            feedback(FeedbackEvent.CAPTURE)
            updateStage()
        }
    }

    init {
        contentDescription = context.getString(com.juice.R.string.strip_open)
        stage.contentDescription = context.getString(com.juice.R.string.strip_dismiss)
        setOnClickListener { onOpen() }
    }

    /** Adds the handle's window, centred on ([x], [y]) screen px, and its stage. */
    fun show(x: Float, y: Float) {
        centreX = x
        centreY = y
        place()
        windowManager.addView(stage, stage.params)
        windowManager.addView(this, params)
        stageAttached = true
        windowAttached = true
    }

    /**
     * Shows the stage only, drawing the slice at [from], and flies it to the handle's place; the
     * handle's own window, which takes touches, is added once it has landed, then [onLanded] is called.
     * Without [animate] it lands at once.
     */
    fun flyHome(x: Float, y: Float, from: SliceSpot, animate: Boolean, onStageDrawn: () -> Unit, onLanded: () -> Unit) {
        centreX = x
        centreY = y
        place()
        windowManager.addView(stage, stage.params)
        stageAttached = true
        val home = SliceSpot(x, y, logo)
        val land = {
            windowManager.addView(this, params)
            windowAttached = true
            whenDrawn { stage.slice = null; stage.visibility = GONE }
            onLanded()
        }
        if (!animate) {
            land()
            onStageDrawn()
            return
        }
        stage.slice = SliceStage.Slice(from.x, from.y, from.size, style, slices)
        stage.visibility = VISIBLE
        stage.whenDrawn(onStageDrawn)
        flight = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIGHT_MILLIS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val at = from.lerp(home, it.animatedValue as Float)
                stage.slice = SliceStage.Slice(at.x, at.y, at.size, style, slices)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (flight !== animation) return
                    flight = null
                    land()
                }
            })
            start()
        }
    }

    /** Removes the handle's window and its stage; safe to call again. */
    fun dismiss() {
        springBack?.cancel()
        cancelFlight()
        if (windowAttached) windowManager.removeView(this)
        windowAttached = false
        dismissStage()
    }

    /** Removes the stage only; safe to call again. */
    fun dismissStage() {
        if (stageAttached) windowManager.removeView(stage)
        stageAttached = false
    }

    private fun cancelFlight() {
        val running = flight
        flight = null
        running?.cancel()
    }

    /**
     * Takes the handle's touch window away but keeps the stage drawing, and flies the slice
     * from where it is now to [target], then calls [onArrived]. Without [animate] it arrives at once.
     */
    fun flyTo(target: SliceSpot, animate: Boolean, onArrived: () -> Unit) {
        springBack?.cancel()
        springBack = null
        removeCallbacks(hold)
        if (windowAttached) windowManager.removeView(this)
        windowAttached = false
        stage.ring = null
        if (!animate) {
            onArrived()
            return
        }
        val from = stage.slice?.let { SliceSpot(it.x, it.y, it.size) } ?: SliceSpot(centreX, centreY, logo)
        stage.slice = SliceStage.Slice(from.x, from.y, from.size, style, slices)
        stage.visibility = VISIBLE
        flight = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIGHT_MILLIS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val at = from.lerp(target, it.animatedValue as Float)
                stage.slice = SliceStage.Slice(at.x, at.y, at.size, style, slices)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (flight !== animation) return
                    flight = null
                    onArrived()
                }
            })
            start()
        }
    }

    /** Moves the handle to ([x], [y]) unless a press is under way. */
    fun moveTo(x: Float, y: Float) {
        if (gesture.pressed || handedOff) return
        centreX = x
        centreY = y
        place()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                springBack?.cancel()
                gesture.down(event.rawX, event.rawY)
                handOff()
                postDelayed(hold, HOLD_MILLIS)
            }
            MotionEvent.ACTION_MOVE -> {
                val crossed = gesture.move(event.rawX, event.rawY, radius())
                if (!gesture.moving && gesture.pull(radius()) > 0f) removeCallbacks(hold)
                if (crossed) feedback(FeedbackEvent.SNAP)
                val over = gesture.moving && isOverDismiss()
                if (over && !overTarget) feedback(FeedbackEvent.SNAP)
                overTarget = over
                updateStage()
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                val (x, y) = slicePosition()
                val over = gesture.moving && isOverDismiss()
                overTarget = false
                when (gesture.up(over)) {
                    Release.DISMISS -> {
                        stage.dismissTarget = null
                        onDismiss()
                    }
                    Release.OPEN -> performClick()
                    Release.MOVED -> {
                        feedback(FeedbackEvent.CAPTURE)
                        val (lx, ly) = onMoved(x, y)
                        centreX = lx
                        centreY = ly
                        place()
                        takeBack()
                    }
                    Release.NONE -> returnHome(x, y)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                val (x, y) = slicePosition()
                overTarget = false
                gesture.cancel()
                returnHome(x, y)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!handedOff) painter.draw(canvas, width / 2f, height / 2f, logo, style, slices, active = 0)
    }

    /** Where the slice is drawn now, in screen px: following a move, or out towards the ring. */
    private fun slicePosition(): Pair<Float, Float> {
        if (gesture.moving) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            val margin = side / 2f
            return (centreX + gesture.dx).coerceIn(margin, bounds.width() - margin) to
                (centreY + gesture.dy).coerceIn(margin, bounds.height() - margin)
        }
        val r = radius()
        if (!gesture.swiping || gesture.pull(r) == 0f) return centreX to centreY
        // The slice follows the finger out to the ring and no further.
        val d = hypot(gesture.dx, gesture.dy)
        val k = if (d > r) r / d else 1f
        return centreX + gesture.dx * k to centreY + gesture.dy * k
    }

    /** The dismiss target, in screen px: centred horizontally, near the bottom, clear of the navigation bar. */
    private fun dismissSpot(): SliceStage.DismissTarget {
        val metrics = windowManager.maximumWindowMetrics
        val bar = metrics.windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        val r = DISMISS_RADIUS_DP * density
        return SliceStage.DismissTarget(
            metrics.bounds.width() / 2f, metrics.bounds.bottom - bar - r - DISMISS_MARGIN_DP * density, r, overTarget,
        )
    }

    private fun isOverDismiss(): Boolean {
        val (x, y) = slicePosition()
        val t = dismissSpot()
        return overDismissTarget(x, y, t.x, t.y, t.radius)
    }

    /** Shows the stage and stops drawing here once the stage's first frame is on screen. */
    private fun handOff() {
        updateStage()
        stage.visibility = VISIBLE
        stage.whenDrawn {
            if (gesture.pressed || springBack != null) {
                handedOff = true
                invalidate()
            }
        }
    }

    /** Draws the slice here again, then hides the stage once this window's frame is on screen. */
    private fun takeBack() {
        stage.dismissTarget = null
        springBack = null
        handedOff = false
        invalidate()
        whenDrawn {
            if (!gesture.pressed && springBack == null) {
                stage.slice = null
                stage.visibility = GONE
            }
        }
    }

    /** Springs the slice back from ([x], [y]) to the handle, then takes it back. */
    private fun returnHome(x: Float, y: Float) {
        if (reduceMotion() || (x == centreX && y == centreY)) {
            takeBack()
            return
        }
        stage.ring = null
        stage.dismissTarget = null
        springBack = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SPRING_MILLIS
            interpolator = OvershootInterpolator(2f)
            addUpdateListener {
                val t = it.animatedValue as Float
                stage.slice = SliceStage.Slice(centreX + (x - centreX) * t, centreY + (y - centreY) * t, logo, style, slices)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (springBack === animation) takeBack()
                }
            })
            start()
        }
    }

    private fun updateStage() {
        val (x, y) = slicePosition()
        val scale = if (gesture.moving) MOVE_SCALE else PRESS_SCALE
        stage.slice = SliceStage.Slice(x, y, logo * scale, style, slices)
        stage.dismissTarget = if (gesture.moving) dismissSpot() else null
        val r = radius()
        stage.ring = if (gesture.swiping) SliceStage.Ring(centreX, centreY, r, gesture.pull(r), gesture.ready, accent) else null
    }

    private fun place() {
        params.x = (centreX - side / 2f).roundToInt()
        params.y = (centreY - side / 2f).roundToInt()
        if (isAttachedToWindow) windowManager.updateViewLayout(this, params)
    }

    companion object {
        const val LOGO_DP = 56f
        private const val PAD_DP = 12f

        /** How close to the screen's edge the handle's centre may sit: its window stays whole on screen. */
        const val EDGE_DP = LOGO_DP / 2 + PAD_DP

        /** How close to a system bar or cutout the handle's centre may sit: the logo stays clear of it. */
        const val LOGO_HALF_DP = LOGO_DP / 2
        private const val MOVE_SLOP_DP = 6f
        private const val HOLD_MILLIS = 420L
        private const val SPRING_MILLIS = 320L
        private const val FLIGHT_MILLIS = REVEAL_MILLIS.toLong()
        private const val PRESS_SCALE = 1.06f
        private const val MOVE_SCALE = 1.22f
        private const val DISMISS_RADIUS_DP = 32f
        private const val DISMISS_MARGIN_DP = 40f
    }
}

/** Runs [action] on the main thread once this view's next frame is on screen. */
internal fun View.whenDrawn(action: () -> Unit) {
    viewTreeObserver.registerFrameCommitCallback { post(action) }
    invalidate()
}

/**
 * A full-screen window that never takes touches, drawing the swipe ring and the slice while the
 * handle is pressed. It is gone the rest of the time, so it never sits over other apps' touches.
 */
internal class SliceStage(context: Context) : View(context) {
    data class Slice(val x: Float, val y: Float, val size: Float, val style: SliceStyle, val slices: Int)
    data class DismissTarget(val x: Float, val y: Float, val radius: Float, val highlighted: Boolean)
    data class Ring(val x: Float, val y: Float, val radius: Float, val pull: Float, val ready: Boolean, val accent: Int)

    var slice: Slice? = null
        set(value) { field = value; invalidate() }
    var ring: Ring? = null
        set(value) { field = value; invalidate() }
    var dismissTarget: DismissTarget? = null
        set(value) { field = value; invalidate() }

    private val painter = SliceLogoPainter()
    private val density = resources.displayMetrics.density
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    init {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        dismissTarget?.let { drawDismissTarget(canvas, it) }
        ring?.let { painter.drawRing(canvas, it.x, it.y, it.radius, it.pull, it.ready, it.accent, density) }
        slice?.let { painter.draw(canvas, it.x, it.y, it.size, it.style, it.slices, active = 0) }
    }

    private fun drawDismissTarget(canvas: Canvas, t: DismissTarget) {
        val scale = if (t.highlighted) 1.2f else 1f
        targetPaint.style = Paint.Style.FILL
        targetPaint.color = if (t.highlighted) 0xFFE53935.toInt() else 0xB3000000.toInt()
        canvas.drawCircle(t.x, t.y, t.radius * scale, targetPaint)
        targetPaint.style = Paint.Style.STROKE
        targetPaint.strokeCap = Paint.Cap.ROUND
        targetPaint.strokeWidth = 3f * density
        targetPaint.color = Color.WHITE
        val a = t.radius * 0.35f * scale
        canvas.drawLine(t.x - a, t.y - a, t.x + a, t.y + a, targetPaint)
        canvas.drawLine(t.x - a, t.y + a, t.x + a, t.y - a, targetPaint)
    }
}
