package com.juice.ui.reservoir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Path
import android.util.Log
import android.view.Surface
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import com.juice.core.physics.RippleField
import com.juice.core.physics.SurfaceWaves

internal const val AGSL ="""
uniform float2 u_resolution;
uniform float  u_time;
uniform float  u_fill;
uniform float2 u_gravity;   // unit vector, screen axes (+x right, +y down), pointing to the side the liquid rests on
uniform float  u_level;     // plane offset along u_gravity (view-height units) that holds u_fill of the area
uniform shader u_heights;   // 48x1 bitmap, red = surface height * 0.5 + 0.5
uniform shader u_ripples;   // 24x16 bitmap, red = ripple height * 0.5 + 0.5
layout(color) uniform half4 u_color;

half4 main(float2 fragCoord) {
    if (u_fill < 0.005) return half4(0.0);   // empty: no stray crest along the bottom edge
    float2 st = fragCoord / u_resolution;
    st.y = 1.0 - st.y;
    float aspect = u_resolution.x / u_resolution.y;

    // Liquid lies on the u_gravity side of a plane. Positions are in view-height units, y down.
    float2 p = float2(st.x * aspect, 1.0 - st.y);
    float2 n = u_gravity;
    float2 t = float2(n.y, -n.x);   // along the surface; (1, 0) when upright
    float ta = 0.0;
    float tb = aspect * t.x;
    float tc = t.y;
    float td = aspect * t.x + t.y;
    float tmin = min(min(ta, tb), min(tc, td));
    float tmax = max(max(ta, tb), max(tc, td));
    float u = (dot(p, t) - tmin) / max(tmax - tmin, 0.0001);

    float f = clamp(u, 0.0, 1.0) * 47.0;
    int i0 = int(min(floor(f), 46.0));
    // Heights live in a bitmap: AGSL only allows constant-index-expressions on uniform arrays.
    float h0 = u_heights.eval(float2(float(i0) + 0.5, 0.5)).r * 2.0 - 1.0;
    float h1 = u_heights.eval(float2(float(i0) + 1.5, 0.5)).r * 2.0 - 1.0;
    float h = mix(h0, h1, f - float(i0));
    float surface = u_level - h * 0.06;

    float below = dot(p, n) - surface;   // distance under the surface along gravity
    if (below < 0.0) return half4(0.0);

    float bottom = max(max(0.0, aspect * n.x), max(n.y, aspect * n.x + n.y));
    float depth = clamp(below / max(bottom - surface, 0.05), 0.0, 1.0);
    half3 c = mix(u_color.rgb, u_color.rgb * 0.6, half(depth));

    float crest = smoothstep(0.015, 0.0, below);
    c += half3(0.3) * half(crest);

    // Ripples: refract the lookup by the ripple slope and shade the crests in the flavour colour.
    float2 rp = float2(st.x * 24.0, (1.0 - st.y) * 16.0);
    float r  = u_ripples.eval(rp).r - 0.5;
    float rx = u_ripples.eval(rp + float2(1.0, 0.0)).r - r - 0.5;
    c += u_color.rgb * half(r * 0.8 + abs(rx) * 0.6);

    return half4(c * 0.9, 0.9);
}
"""

/** Direction the liquid rests toward, in screen axes (+x right, +y down), as a unit vector. */
internal data class Gravity(val x: Float, val y: Float) {
    companion object {
        /** Liquid resting on the bottom edge. */
        val DOWN = Gravity(0f, 1f)
    }
}

/**
 * Which way is down on the screen, from an accelerometer reading ([ax], [ay], [az] in m/s², device axes) and the
 * [rotation] of the display (a [Surface] ROTATION_ constant). A phone lying flat has almost no in-plane gravity,
 * so it blends to [Gravity.DOWN] instead of following sensor noise.
 */
internal fun screenGravity(ax: Float, ay: Float, az: Float, rotation: Int): Gravity {
    val sx: Float
    val sy: Float
    when (rotation) {
        Surface.ROTATION_90 -> { sx = -ay; sy = ax }
        Surface.ROTATION_180 -> { sx = -ax; sy = -ay }
        Surface.ROTATION_270 -> { sx = ay; sy = -ax }
        else -> { sx = ax; sy = ay }
    }
    // The accelerometer reads the reaction to gravity: upright it reads +y, so down on screen is (-sx, +sy).
    val gx = 0f - sx
    val gy = sy
    val inPlane = sqrt(gx * gx + gy * gy)
    val total = sqrt(inPlane * inPlane + az * az)
    val ratio = if (total < 1e-3f) 0f else inPlane / total
    val w = ((ratio - FLAT_BELOW) / (FLAT_ABOVE - FLAT_BELOW)).coerceIn(0f, 1f)
    if (w == 0f) return Gravity.DOWN
    val x = gx / inPlane * w
    val y = gy / inPlane * w + (1f - w)
    val len = sqrt(x * x + y * y)
    return Gravity(x / len, y / len)
}

private const val FLAT_BELOW = 0.15f
private const val FLAT_ABOVE = 0.35f

/** Corners of the part of the rectangle (0,0)-([width],1) with `x*nx + y*ny >= level`, as x0,y0,x1,y1,... */
internal fun liquidPolygon(nx: Double, ny: Double, level: Double, width: Double): DoubleArray {
    val xs = doubleArrayOf(0.0, width, width, 0.0)
    val ys = doubleArrayOf(0.0, 0.0, 1.0, 1.0)
    val out = ArrayList<Double>(10)
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        val da = xs[i] * nx + ys[i] * ny - level
        val db = xs[j] * nx + ys[j] * ny - level
        if (da >= 0.0) { out += xs[i]; out += ys[i] }
        if ((da >= 0.0) != (db >= 0.0)) {
            val k = da / (da - db)
            out += xs[i] + (xs[j] - xs[i]) * k
            out += ys[i] + (ys[j] - ys[i]) * k
        }
    }
    return out.toDoubleArray()
}

private fun polygonArea(p: DoubleArray): Double {
    var sum = 0.0
    val n = p.size / 2
    for (i in 0 until n) {
        val j = (i + 1) % n
        sum += p[2 * i] * p[2 * j + 1] - p[2 * j] * p[2 * i + 1]
    }
    return abs(sum) / 2.0
}

/**
 * Offset of the liquid surface plane along [gravity] (unit vector), in units of the view height, for a view
 * [aspect] (width / height) wide: the liquid is where `x*gx + y*gy >= offset`, and covers [fill] of the view.
 */
internal fun liquidLevel(gravity: Gravity, fill: Float, aspect: Float): Float {
    val nx = gravity.x.toDouble()
    val ny = gravity.y.toDouble()
    val w = aspect.toDouble()
    val dots = doubleArrayOf(0.0, w * nx, ny, w * nx + ny)
    var lo = dots.min()
    var hi = dots.max()
    val target = fill.coerceIn(0f, 1f) * w
    repeat(40) {
        val mid = (lo + hi) / 2
        if (polygonArea(liquidPolygon(nx, ny, mid, w)) > target) lo = mid else hi = mid
    }
    return ((lo + hi) / 2).toFloat()
}

/**
 * Thirst Reservoir: liquid drawn by an AGSL shader, lying against whichever edge is down and sloshing via [SurfaceWaves] and [RippleField] fed by gravity changes,
 * shakes, [kick], [scrollImpulse], [disturb] and [wake]. Animates (and listens to the accelerometer) only while attached and visible.
 */
class ThirstReservoirView(context: Context) : View(context), SensorEventListener {
    /** Fill level, 0 (thirsty) to 1 (satiated). */
    var fill = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /**
     * Liquid colour (ARGB), set from the player's flavour. setColorUniform always writes four
     * values, so the shader's u_color must be a half4: a half3 throws when the view is built.
     */
    var color = Color.rgb(255, 170, 40)
        set(value) {
            field = value
            shader?.setColorUniform("u_color", value)
            fallbackPaint.color = value
            fallbackPaint.alpha = 230
            particlePaint.color = lighten(value)
            invalidate()
        }

    /** Golden Honey: heavier liquid, so slower waves that settle faster. */
    var viscous = false
        set(value) {
            field = value
            waves.viscosity = if (value) 1f else 0f
        }

    /** Reduce motion turns these off: no gravity/shake/touch input, the liquid rests flat on the bottom. */
    var sloshEnabled = true
        set(value) {
            field = value
            if (!value) resetGravity()
        }
    var particlesEnabled = true

    /** Carbonated flavours: a constant trickle of rising bubbles, and a burst when the liquid is touched. */
    var fizz = false

    private val rippleBitmap = Bitmap.createBitmap(RippleField.WIDTH, RippleField.HEIGHT, Bitmap.Config.ARGB_8888)
    private val heightsBitmap = Bitmap.createBitmap(SurfaceWaves.COLUMNS, 1, Bitmap.Config.ARGB_8888)
    private val heightPixels = IntArray(SurfaceWaves.COLUMNS)

    /** Null when the AGSL fails to compile; [onDraw] then falls back to a plain fill. */
    private val shader: RuntimeShader? = try {
        RuntimeShader(AGSL).apply {
            setColorUniform("u_color", color)
            setInputShader("u_ripples", BitmapShader(rippleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            setInputShader("u_heights", BitmapShader(heightsBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }
    } catch (e: RuntimeException) {
        Log.e(TAG, "Reservoir shader failed to compile; drawing a plain fill", e)
        null
    }
    private val paint = Paint().apply { this.shader = this@ThirstReservoirView.shader }
    private val fallbackPath = Path()
    private val fallbackPaint = Paint().apply { color = this@ThirstReservoirView.color; alpha = 230 }
    internal val waves = SurfaceWaves()
    internal val ripples = RippleField()
    private val flatHeights = FloatArray(SurfaceWaves.COLUMNS)
    private val ripplePixels = IntArray(RippleField.WIDTH * RippleField.HEIGHT)
    private var gravity = 0f
    private var gravityReady = false
    private var lastAx = 0f
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var lowX = 0f
    private var lowY = 0f
    private var lowZ = 0f
    private var lowReady = false
    private var targetAngle = 0f
    /** Angle of the animated gravity from straight down, positive toward screen right, and its speed. */
    private var angle = 0f
    private var angularVelocity = 0f
    private var time = 0f
    private var lastFrameNanos = 0L
    private var running = false

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { nanos ->
        if (!running) return@FrameCallback
        val dt = if (lastFrameNanos == 0L) 0f else ((nanos - lastFrameNanos) / 1e9f).coerceAtMost(0.05f)
        lastFrameNanos = nanos
        time = (time + dt) % TIME_WRAP
        stepSimulation(dt)
        // Satiated: the drink keeps fizzing.
        if (particlesEnabled && fill > SATIATED_FILL && Random.nextFloat() < dt * 14f) emitBubble()
        else if (particlesEnabled && fizz && fill > 0f && Random.nextFloat() < dt * FIZZ_RATE) emitBubble()
        particles.removeAll { it.life <= 0f }
        particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
        invalidate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float)

    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint().apply { color = Color.rgb(255, 220, 120); isAntiAlias = true }
    internal val particleCount get() = particles.size

    /** Bursts particles up from the liquid surface, for reaching Satiated. */
    fun emitParticles(count: Int = 40) {
        if (!particlesEnabled) return
        repeat(minOf(count, MAX_PARTICLES - particles.size)) {
            particles += Particle(
                Random.nextFloat() * width, height * (1f - fill),
                (Random.nextFloat() - 0.5f) * 400f, -Random.nextFloat() * 700f - 100f, 1f,
            )
        }
        invalidate()
    }

    private fun emitBubble() {
        if (particles.size >= MAX_PARTICLES) return
        particles += Particle(
            Random.nextFloat() * width, height.toFloat(),
            0f, -(50f + Random.nextFloat() * 70f) * resources.displayMetrics.density, 1.4f + Random.nextFloat(),
        )
    }

    /** Sloshes the liquid by changing its velocity at once, e.g. on a carousel snap or a completion. */
    fun kick(velocity: Float) {
        if (sloshEnabled) waves.kick(velocity * KICK_SCALE)
    }

    /** Adds a slosh impulse, e.g. from scroll velocity. */
    fun scrollImpulse(impulse: Float) {
        if (sloshEnabled) waves.kick(impulse * SCROLL_SCALE)
    }

    /** A touch at ([x], [y]), both 0..1 across the view: a local wave on the surface and a ripple under it. */
    fun disturb(x: Float, y: Float, strength: Float) {
        if (fizz && particlesEnabled && fill > 0f) repeat(minOf(FIZZ_BURST, MAX_PARTICLES - particles.size)) { emitBubble() }
        if (!sloshEnabled) return
        waves.disturb(x, strength)
        ripples.disturb(x, y, strength)
    }

    /** A finger dragged from ([x0], [y0]) to ([x1], [y1]), 0..1 across the view, leaves a wake of ripples. */
    fun wake(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (sloshEnabled) ripples.wake(x0, y0, x1, y1, WAKE_STRENGTH)
    }

    internal fun stepSimulation(dt: Float) {
        stepGravity(dt)
        waves.step(dt)
        ripples.step(dt)
    }

    /** Springs the drawn gravity toward the sensed one, sloshing the surface in proportion to how far it swings. */
    private fun stepGravity(dt: Float) {
        if (!sloshEnabled) {
            resetGravity()
            return
        }
        val step = dt.coerceIn(0f, MAX_GRAVITY_DT)
        var goal = targetAngle
        while (goal - angle > PI) goal -= TWO_PI
        while (goal - angle < -PI) goal += TWO_PI
        angularVelocity += (GRAVITY_STIFFNESS * (goal - angle) - GRAVITY_DAMPING * angularVelocity) * step
        val swing = angularVelocity * step
        angle += swing
        if (angle > PI) angle -= TWO_PI else if (angle < -PI) angle += TWO_PI
        if (swing != 0f) waves.kick(swing * SWING_SLOSH)
    }

    private fun resetGravity() {
        lowReady = false
        targetAngle = 0f
        angle = 0f
        angularVelocity = 0f
    }

    override fun onDraw(canvas: Canvas) {
        if (shader == null) {
            if (fill >= MIN_FILL) {
                val aspect = width.toFloat() / height
                val g = Gravity(sin(angle), cos(angle))
                val poly = liquidPolygon(g.x.toDouble(), g.y.toDouble(), liquidLevel(g, fill, aspect).toDouble(), aspect.toDouble())
                fallbackPath.rewind()
                for (i in 0 until poly.size / 2) {
                    val px = (poly[2 * i] * height).toFloat()
                    val py = (poly[2 * i + 1] * height).toFloat()
                    if (i == 0) fallbackPath.moveTo(px, py) else fallbackPath.lineTo(px, py)
                }
                fallbackPath.close()
                canvas.drawPath(fallbackPath, fallbackPaint)
            }
        } else {
            shader.setFloatUniform("u_resolution", width.toFloat(), height.toFloat())
            shader.setFloatUniform("u_time", time)
            shader.setFloatUniform("u_fill", fill)
            val g = Gravity(sin(angle), cos(angle))
            shader.setFloatUniform("u_gravity", g.x, g.y)
            shader.setFloatUniform("u_level", liquidLevel(g, fill, width.toFloat() / height))
            val hs = if (sloshEnabled) waves.heights else flatHeights
            for (i in hs.indices) heightPixels[i] = Color.rgb(((hs[i].coerceIn(-1f, 1f) * 0.5f + 0.5f) * 255f).roundToInt(), 0, 0)
            heightsBitmap.setPixels(heightPixels, 0, hs.size, 0, 0, hs.size, 1)
            val rh = ripples.heights
            for (i in rh.indices) {
                val level = ((if (sloshEnabled) rh[i] else 0f) * 0.5f + 0.5f) * 255f
                ripplePixels[i] = Color.rgb(level.toInt(), 0, 0)
            }
            rippleBitmap.setPixels(ripplePixels, 0, RippleField.WIDTH, 0, 0, RippleField.WIDTH, RippleField.HEIGHT)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        particles.forEach {
            particlePaint.alpha = (it.life.coerceIn(0f, 0.9f) * 255).toInt()
            canvas.drawCircle(it.x, it.y, 3f * resources.displayMetrics.density, particlePaint)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) start() else stop()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stop() {
        if (!running) return
        running = false
        sensors.unregisterListener(this)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        resetGravity()
        gravityReady = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        onAcceleration(event.values[0], event.values[1], event.values[2])
    }

    /**
     * Gravity direction comes from the low-passed reading, in screen axes. A shake is a spike in the magnitude above its low-passed
     * (gravity) level; it kicks the surface harder than a slow tilt does.
     */
    internal fun onAcceleration(x: Float, y: Float, z: Float) {
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
        if (!gravityReady) {
            gravity = magnitude
            gravityReady = true
        } else {
            gravity = GRAVITY_ALPHA * gravity + (1f - GRAVITY_ALPHA) * magnitude
        }
        val spike = abs(magnitude - gravity)
        val dx = x - lastAx
        lastAx = x
        if (!sloshEnabled) return
        if (!lowReady) {
            lowX = x; lowY = y; lowZ = z
            lowReady = true
        } else {
            lowX = LOW_PASS * lowX + (1f - LOW_PASS) * x
            lowY = LOW_PASS * lowY + (1f - LOW_PASS) * y
            lowZ = LOW_PASS * lowZ + (1f - LOW_PASS) * z
        }
        val down = screenGravity(lowX, lowY, lowZ, display?.rotation ?: Surface.ROTATION_0)
        targetAngle = atan2(down.x, down.y)
        if (spike > SHAKE_THRESHOLD) waves.kick(-sign(dx) * SHAKE_STRENGTH)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun lighten(c: Int) = Color.rgb(
        (Color.red(c) + 255) / 2, (Color.green(c) + 255) / 2, (Color.blue(c) + 255) / 2,
    )

    private companion object {
        const val TAG = "ThirstReservoirView"
        const val MIN_FILL = 0.005f
        const val SATIATED_FILL = 0.75f
        const val MAX_PARTICLES = 120
        const val FIZZ_RATE = 8f
        const val FIZZ_BURST = 12
        const val TIME_WRAP = 1000f
        const val TWO_PI = (2 * PI).toFloat()
        const val LOW_PASS = 0.8f
        const val GRAVITY_STIFFNESS = 40f
        const val GRAVITY_DAMPING = 10f
        const val MAX_GRAVITY_DT = 1f / 30
        const val SWING_SLOSH = 2f
        const val GRAVITY_ALPHA = 0.9f
        const val SHAKE_THRESHOLD = 6f
        const val SHAKE_STRENGTH = 1.5f
        const val KICK_SCALE = 0.1f
        const val SCROLL_SCALE = 0.02f
        const val WAKE_STRENGTH = 0.3f
    }
}
