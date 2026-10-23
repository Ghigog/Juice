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
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random
import com.juice.core.physics.RippleField
import com.juice.core.physics.SurfaceWaves

private const val AGSL = """
uniform float2 u_resolution;
uniform float  u_time;
uniform float  u_fill;
uniform float  u_heights[48];
uniform shader u_ripples;   // 24x16 bitmap, red = ripple height * 0.5 + 0.5
layout(color) uniform half4 u_color;

half4 main(float2 fragCoord) {
    if (u_fill < 0.005) return half4(0.0);   // empty: no stray crest along the bottom edge
    float2 st = fragCoord / u_resolution;
    st.y = 1.0 - st.y;

    float aspect = u_resolution.x / u_resolution.y;
    float x = st.x * aspect;

    float f = clamp(st.x, 0.0, 1.0) * 47.0;
    int i0 = int(min(floor(f), 46.0));
    float h = mix(u_heights[i0], u_heights[i0 + 1], f - float(i0));
    float surface = u_fill + h * 0.06;

    if (st.y > surface) return half4(0.0);

    float depth = clamp((surface - st.y) / max(surface, 0.05), 0.0, 1.0);
    half3 c = mix(u_color.rgb, u_color.rgb * 0.6, half(depth));

    float crest = smoothstep(0.015, 0.0, surface - st.y);
    c += half3(0.3) * half(crest);

    // Ripples: refract the lookup by the ripple slope and shade the crests in the flavour colour.
    float2 rp = float2(st.x * 24.0, (1.0 - st.y) * 16.0);
    float r  = u_ripples.eval(rp).r - 0.5;
    float rx = u_ripples.eval(rp + float2(1.0, 0.0)).r - r - 0.5;
    c += u_color.rgb * half(r * 0.8 + abs(rx) * 0.6);

    return half4(c * 0.9, 0.9);
}
"""

/**
 * Thirst Reservoir: liquid drawn by an AGSL shader, sloshing via [SurfaceWaves] and [RippleField] fed by tilt,
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
            shader.setColorUniform("u_color", value)
            particlePaint.color = lighten(value)
            invalidate()
        }

    /** Golden Honey: heavier liquid, so slower waves that settle faster. */
    var viscous = false
        set(value) {
            field = value
            waves.viscosity = if (value) 1f else 0f
        }

    /** Reduce motion turns these off: no tilt/shake/touch input to the waves, the liquid renders flat. */
    var sloshEnabled = true
        set(value) {
            field = value
            if (!value) tiltImpulse = 0f
        }
    var particlesEnabled = true

    private val rippleBitmap = Bitmap.createBitmap(RippleField.WIDTH, RippleField.HEIGHT, Bitmap.Config.ARGB_8888)
    private val shader = RuntimeShader(AGSL).apply {
        setColorUniform("u_color", color)
        setInputShader("u_ripples", BitmapShader(rippleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }
    private val paint = Paint().apply { this.shader = this@ThirstReservoirView.shader }
    internal val waves = SurfaceWaves()
    internal val ripples = RippleField()
    private val flatHeights = FloatArray(SurfaceWaves.COLUMNS)
    private val ripplePixels = IntArray(RippleField.WIDTH * RippleField.HEIGHT)
    private var gravity = 0f
    private var gravityReady = false
    private var lastAx = 0f
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var tiltImpulse = 0f
    private var tiltY = 0f
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
        particles.removeAll { it.life <= 0f }
        particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
        invalidate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float)

    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint().apply { color = Color.rgb(255, 220, 120); isAntiAlias = true }

    /** Bursts particles up from the liquid surface, for reaching Satiated. */
    fun emitParticles(count: Int = 40) {
        if (!particlesEnabled) return
        repeat(count) {
            particles += Particle(
                Random.nextFloat() * width, height * (1f - fill),
                (Random.nextFloat() - 0.5f) * 400f, -Random.nextFloat() * 700f - 100f, 1f,
            )
        }
        invalidate()
    }

    private fun emitBubble() {
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
        if (!sloshEnabled) return
        waves.disturb(x, strength)
        ripples.disturb(x, y, strength)
    }

    /** A finger dragged from ([x0], [y0]) to ([x1], [y1]), 0..1 across the view, leaves a wake of ripples. */
    fun wake(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (sloshEnabled) ripples.wake(x0, y0, x1, y1, WAKE_STRENGTH)
    }

    internal fun stepSimulation(dt: Float) {
        waves.tiltX = tiltImpulse
        waves.tiltY = tiltY
        waves.step(dt)
        ripples.step(dt)
    }

    override fun onDraw(canvas: Canvas) {
        shader.setFloatUniform("u_resolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_fill", fill)
        shader.setFloatUniform("u_heights", if (sloshEnabled) waves.heights else flatHeights)
        val rh = ripples.heights
        for (i in rh.indices) {
            val level = ((if (sloshEnabled) rh[i] else 0f) * 0.5f + 0.5f) * 255f
            ripplePixels[i] = Color.rgb(level.toInt(), 0, 0)
        }
        rippleBitmap.setPixels(ripplePixels, 0, RippleField.WIDTH, 0, 0, RippleField.WIDTH, RippleField.HEIGHT)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
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
        tiltImpulse = 0f
        tiltY = 0f
        gravityReady = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        onAcceleration(event.values[0], event.values[1], event.values[2])
    }

    /**
     * Tilt comes from the x and y axes. A shake is a spike in the magnitude above its low-passed
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
        tiltImpulse = -x / GRAVITY_EARTH
        tiltY = y / GRAVITY_EARTH
        if (spike > SHAKE_THRESHOLD) waves.kick(-sign(dx) * SHAKE_STRENGTH)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun lighten(c: Int) = Color.rgb(
        (Color.red(c) + 255) / 2, (Color.green(c) + 255) / 2, (Color.blue(c) + 255) / 2,
    )

    private companion object {
        const val SATIATED_FILL = 0.75f
        const val TIME_WRAP = 1000f
        const val GRAVITY_EARTH = 9.81f
        const val GRAVITY_ALPHA = 0.9f
        const val SHAKE_THRESHOLD = 6f
        const val SHAKE_STRENGTH = 1.5f
        const val KICK_SCALE = 0.1f
        const val SCROLL_SCALE = 0.02f
        const val WAKE_STRENGTH = 0.3f
    }
}
