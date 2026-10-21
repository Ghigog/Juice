package com.juice.ui.reservoir

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Choreographer
import android.view.View
import kotlin.random.Random
import com.juice.core.physics.SloshSpring

private const val AGSL = """
uniform float2 u_resolution;
uniform float  u_time;
uniform float  u_fill;
uniform float  u_slosh;
layout(color) uniform half3 u_color;

half4 main(float2 fragCoord) {
    float2 st = fragCoord / u_resolution;
    st.y = 1.0 - st.y;

    float aspect = u_resolution.x / u_resolution.y;
    float x = st.x * aspect;

    float tilt  = u_slosh * (st.x - 0.5) * 0.12;
    float amp   = 0.5 + abs(u_slosh);
    float wave  = sin(x * 4.0 + u_time * 3.0) * 0.02
                + cos(x * 7.0 - u_time * 2.5) * 0.01;
    float surface = u_fill + tilt + wave * amp;

    if (st.y > surface) return half4(0.0);

    float depth = clamp((surface - st.y) / max(surface, 0.05), 0.0, 1.0);
    half3 c = mix(u_color, u_color * 0.6, half(depth));

    float crest = smoothstep(0.015, 0.0, surface - st.y);
    c += half3(0.3) * half(crest);

    return half4(c * 0.9, 0.9);
}
"""

/**
 * Thirst Reservoir: liquid drawn by an AGSL shader, sloshing via a [SloshSpring] fed by tilt and
 * [scrollImpulse]. Animates (and listens to the accelerometer) only while attached and visible.
 */
class ThirstReservoirView(context: Context) : View(context), SensorEventListener {
    /** Fill level, 0 (thirsty) to 1 (satiated). */
    var fill = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Liquid colour (ARGB), set from the player's flavour. */
    var color = Color.rgb(255, 170, 40)
        set(value) {
            field = value
            shader.setColorUniform("u_color", value)
            invalidate()
        }

    /** Reduce motion turns these off: no tilt/scroll slosh, no particle bursts. */
    var sloshEnabled = true
    var particlesEnabled = true

    private val shader = RuntimeShader(AGSL).apply { setColorUniform("u_color", color) }
    private val paint = Paint().apply { this.shader = this@ThirstReservoirView.shader }
    private val spring = SloshSpring()
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var tiltImpulse = 0f
    private var pendingImpulse = 0f
    private var time = 0f
    private var lastFrameNanos = 0L
    private var running = false

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { nanos ->
        if (!running) return@FrameCallback
        val dt = if (lastFrameNanos == 0L) 0f else ((nanos - lastFrameNanos) / 1e9f).coerceAtMost(0.05f)
        lastFrameNanos = nanos
        time = (time + dt) % TIME_WRAP
        spring.step(dt, if (sloshEnabled) tiltImpulse + pendingImpulse else 0f)
        pendingImpulse = 0f
        particles.removeAll { it.life <= 0f }
        particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
        invalidate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float)

    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint().apply { color = Color.rgb(255, 220, 120) }

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

    /** Adds a one-frame slosh impulse, e.g. from scroll velocity. */
    fun scrollImpulse(impulse: Float) {
        pendingImpulse += impulse
    }

    override fun onDraw(canvas: Canvas) {
        shader.setFloatUniform("u_resolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_fill", fill)
        shader.setFloatUniform("u_slosh", spring.position)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        particles.forEach {
            particlePaint.alpha = (it.life.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawCircle(it.x, it.y, 6f, particlePaint)
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
    }

    override fun onSensorChanged(event: SensorEvent) {
        tiltImpulse = -event.values[0] * TILT_GAIN
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TIME_WRAP = 1000f
        const val TILT_GAIN = 4f
    }
}
