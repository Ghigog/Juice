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
import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.juice.feedback.FeedbackEvent
import com.juice.ui.overlay.CLOSE_DRAIN_MILLIS
import com.juice.ui.overlay.REVEAL_MILLIS
import com.juice.ui.overlay.SpinMomentum
import com.juice.ui.overlay.spinForPull
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What the handle shows for the deadlines coming: how many are incoming and the spin of the most urgent. */
internal data class HandleNudge(val count: Int, val spinDegPerSec: Float) {
    companion object { val NONE = HandleNudge(0, 0f) }
}

/**
 * Draws the incoming-deadline mark on a slice at ([x], [y]) of [size]: the count as an accent
 * badge (11 sp bold, +1 tracking, a capsule at the badge radius), or, for [dot], a small still
 * accent dot. It sits at the slice corner towards ([towardX], [towardY]), the screen centre.
 */
internal class HandleBadgePainter(private val density: Float) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        isFakeBoldText = true
        letterSpacing = 1f / 11f
        textAlign = Paint.Align.CENTER
        color = 0xFF0B1A22.toInt()
    }

    fun draw(canvas: Canvas, x: Float, y: Float, size: Float, towardX: Float, towardY: Float, count: Int, dot: Boolean, scale: Float, accent: Int) {
        if (count <= 0 || scale <= 0f) return
        val cx = x + (if (towardX >= x) 1 else -1) * size * BADGE_OFFSET
        val cy = y + (if (towardY >= y) 1 else -1) * size * BADGE_OFFSET
        fill.color = accent
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        if (dot) {
            canvas.drawCircle(cx, cy, DOT_RADIUS_DP * density, fill)
        } else {
            val label = if (count > 9) "9+" else count.toString()
            val h = BADGE_HEIGHT_DP * density
            val w = maxOf(h, text.measureText(label) + 2 * BADGE_PAD_DP * density)
            val r = minOf(BADGE_RADIUS_DP * density, h / 2)
            canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, r, r, fill)
            canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2, text)
        }
        canvas.restore()
    }

    private companion object {
        const val BADGE_OFFSET = 0.43f
        const val BADGE_HEIGHT_DP = 18f
        const val BADGE_PAD_DP = 5f
        const val BADGE_RADIUS_DP = 14f
        const val DOT_RADIUS_DP = 5f
    }
}

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
    /** Opens the overlay, handing over the slice's live spin: its angle in degrees and its speed in degrees per second. */
    onOpen: (angle: Float, spinDegPerSec: Float) -> Unit,
) : View(context) {
    var style: SliceStyle = SliceStyle.of(com.juice.core.progression.Flavour.ELECTRIC_CITRUS)
        set(value) { field = value; invalidate(); stage.invalidate() }
    var accent: Int = 0
        set(value) { field = value; invalidate(); syncStageBadge() }
    var slices: Int = 0
        set(value) { field = value; invalidate(); stage.invalidate() }

    /** The deadlines coming: spins the slice at the most urgent one's speed and marks the count. */
    var nudge: HandleNudge = HandleNudge.NONE
        set(value) {
            if (field == value) return
            val countChanged = field.count != value.count
            field = value
            spin.cruise = value.spinDegPerSec
            if (countChanged) {
                contentDescription = if (value.count > 0) {
                    resources.getQuantityString(com.juice.R.plurals.strip_open_deadlines, value.count, value.count)
                } else {
                    context.getString(com.juice.R.string.strip_open)
                }
                popBadge(value.count)
            }
            if (spin.cruise != 0f) startFrames()
        }

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
    private var velocity: VelocityTracker? = null
    /** The slice's spin, kicked by the drag and stepped by [frame] while the stage shows the slice. */
    private var spin = newSpin()
    /** 1 while the spin shows in full; falls to 0 as the slice springs back, fading the spin out. */
    private var settle = 1f
    /** The badge's size, 0..1 with overshoot; the count it is drawn with while it pops out is [badgeCount]. */
    private var badgeScale = 0f
    private var badgeCount = 0
    private var badgePop: ValueAnimator? = null
    private val badgePainter = HandleBadgePainter(density)
    private var frameRunning = false
    private var lastFrameNanos = 0L
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val shown = stage.slice
            if (shown == null && spin.cruise == 0f && spin.boost == 0f) {
                frameRunning = false
                return
            }
            if (lastFrameNanos != 0L) spin.step(frameTimeNanos - lastFrameNanos)
            lastFrameNanos = frameTimeNanos
            if (shown != null && spin.boost != 0f) stage.slice = shown.copy(rotation = rotation())
            if (!handedOff && spin.boost != 0f) invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
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
        setOnClickListener { onOpen(spin.angle, spin.boost) }
    }

    /** A fresh spin at the deadline cruise speed, carrying on from [angle]. */
    private fun newSpin(angle: Float = 0f) = SpinMomentum(enabled = !reduceMotion()).also {
        it.seed(angle)
        it.cruise = nudge.spinDegPerSec
    }

    /** Pops the badge in, out or between counts like a bubble; with reduced motion it just appears. */
    private fun popBadge(count: Int) {
        badgePop?.cancel()
        val marked = count >= 2 || (count == 1 && reduceMotion())
        if (marked) badgeCount = count
        val target = if (marked) 1f else 0f
        // A new count over a shown badge pops again from a little smaller.
        val from = if (badgeScale > 0f && target > 0f) 0.6f else badgeScale
        if (reduceMotion()) {
            badgeScale = target
            syncStageBadge()
            invalidate()
            return
        }
        badgePop = ValueAnimator.ofFloat(from, target).apply {
            duration = POP_MILLIS
            interpolator = if (target > 0f) OvershootInterpolator(2.5f) else DecelerateInterpolator()
            addUpdateListener {
                badgeScale = (it.animatedValue as Float).coerceAtLeast(0f)
                syncStageBadge()
                invalidate()
            }
            start()
        }
    }

    private fun syncStageBadge() {
        stage.badge = SliceStage.Badge(badgeCount, badgeCount == 1, badgeScale, accent)
    }

    /** The slice's live angle in degrees, for the overlay to carry on from. */
    val spinAngle: Float get() = spin.angle

    /** The slice's live spin in degrees per second. */
    val spinSpeed: Float get() = spin.boost

    /** The angle to draw, in (-180, 180] so fading it out by [settle] never swings the slice round. */
    private fun rotation(): Float = (if (spin.angle > 180f) spin.angle - 360f else spin.angle) * settle

    private fun startFrames() {
        if (frameRunning) return
        frameRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frame)
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
        if (spin.cruise != 0f) startFrames()
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
        stage.slice = SliceStage.Slice(from.x, from.y, from.size, style, slices, rotation())
        stage.visibility = VISIBLE
        startFrames()
        stage.whenDrawn(onStageDrawn)
        flight = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIGHT_MILLIS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val at = from.lerp(home, it.animatedValue as Float)
                stage.slice = SliceStage.Slice(at.x, at.y, at.size, style, slices, rotation())
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
        badgePop?.cancel()
        Choreographer.getInstance().removeFrameCallback(frame)
        frameRunning = false
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
        settle = 1f
        if (!animate) {
            onArrived()
            return
        }
        val from = stage.slice?.let { SliceSpot(it.x, it.y, it.size) } ?: SliceSpot(centreX, centreY, logo)
        stage.slice = SliceStage.Slice(from.x, from.y, from.size, style, slices, rotation())
        stage.visibility = VISIBLE
        startFrames()
        flight = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RETURN_FLIGHT_MILLIS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val at = from.lerp(target, it.animatedValue as Float)
                stage.slice = SliceStage.Slice(at.x, at.y, at.size, style, slices, rotation())
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
                spin = newSpin(rotation())
                settle = 1f
                gesture.down(event.rawX, event.rawY)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                handOff()
                postDelayed(hold, HOLD_MILLIS)
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.apply {
                    addMovement(event)
                    computeCurrentVelocity(1000)
                    spin.kick(spinForPull(xVelocity, yVelocity))
                }
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
                releaseVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                releaseVelocity()
                val (x, y) = slicePosition()
                overTarget = false
                gesture.cancel()
                returnHome(x, y)
            }
        }
        return true
    }

    private fun releaseVelocity() {
        velocity?.recycle()
        velocity = null
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (handedOff) return
        canvas.save()
        canvas.rotate(rotation(), width / 2f, height / 2f)
        painter.draw(canvas, width / 2f, height / 2f, logo, style, slices, active = 0)
        canvas.restore()
        val bounds = windowManager.maximumWindowMetrics.bounds
        badgePainter.draw(
            canvas, width / 2f, height / 2f, logo, if (centreX <= bounds.width() / 2f) width.toFloat() else 0f,
            if (centreY <= bounds.height() / 2f) height.toFloat() else 0f, badgeCount, badgeCount == 1, badgeScale, accent,
        )
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
        // The pull's spin has faded out with the spring, so the handle carries on from still at the cruise speed.
        spin = newSpin(rotation())
        settle = 1f
        startFrames()
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
        startFrames()
        springBack = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SPRING_MILLIS
            interpolator = OvershootInterpolator(2f)
            addUpdateListener {
                val t = it.animatedValue as Float
                settle = t.coerceIn(0f, 1f)
                stage.slice = SliceStage.Slice(centreX + (x - centreX) * t, centreY + (y - centreY) * t, logo, style, slices, rotation())
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
        stage.slice = SliceStage.Slice(x, y, logo * scale, style, slices, rotation())
        startFrames()
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
        private const val POP_MILLIS = 380L
        private const val MOVE_SLOP_DP = 6f
        private const val HOLD_MILLIS = 420L
        private const val SPRING_MILLIS = 320L
        private const val FLIGHT_MILLIS = REVEAL_MILLIS.toLong()
        private const val RETURN_FLIGHT_MILLIS = CLOSE_DRAIN_MILLIS.toLong()
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
    data class Slice(val x: Float, val y: Float, val size: Float, val style: SliceStyle, val slices: Int, val rotation: Float = 0f)
    data class DismissTarget(val x: Float, val y: Float, val radius: Float, val highlighted: Boolean)
    data class Badge(val count: Int, val dot: Boolean, val scale: Float, val accent: Int)
    data class Ring(val x: Float, val y: Float, val radius: Float, val pull: Float, val ready: Boolean, val accent: Int)

    var slice: Slice? = null
        set(value) { field = value; invalidate() }
    var ring: Ring? = null
        set(value) { field = value; invalidate() }
    var badge: Badge? = null
        set(value) { field = value; invalidate() }
    var dismissTarget: DismissTarget? = null
        set(value) { field = value; invalidate() }

    private val painter = SliceLogoPainter()
    private val density = resources.displayMetrics.density
    private val badgePainter = HandleBadgePainter(density)
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
        slice?.let {
            canvas.save()
            canvas.rotate(it.rotation, it.x, it.y)
            painter.draw(canvas, it.x, it.y, it.size, it.style, it.slices, active = 0)
            canvas.restore()
            badge?.let { b ->
                badgePainter.draw(
                    canvas, it.x, it.y, it.size, if (it.x <= width / 2f) width.toFloat() else 0f,
                    if (it.y <= height / 2f) height.toFloat() else 0f, b.count, b.dot, b.scale, b.accent,
                )
            }
        }
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
