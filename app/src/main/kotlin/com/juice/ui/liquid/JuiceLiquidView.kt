package com.juice.ui.liquid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.View
import androidx.annotation.ColorInt
import com.juice.core.liquid.Froth
import com.juice.core.liquid.LiquidEngine
import com.juice.core.liquid.LiquidFrame
import com.juice.core.liquid.LiquidFrames
import com.juice.core.liquid.LiquidQuality
import com.juice.core.liquid.LiquidSim
import com.juice.core.liquid.ScreenGravity
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Thirst Reservoir: the juice in the centre card, a particle fluid ([LiquidEngine]) shaded by an AGSL
 * shader. [fill] (the Thirst Meter) sets how much juice there is; it reads the accelerometer while visible,
 * froths when shaken, sloshes on [kick], [touchDown] and friends, and stops its frame loop entirely once
 * the juice has settled. Needs a hardware-accelerated window; on a software canvas it draws plain dots.
 *
 * The engine lives on [runner]'s thread: everything that touches it is posted there, and each step is
 * handed to the UI as a [LiquidFrame], so physics never holds up a UI frame. [quality] trades particle size
 * and shading resolution for speed.
 *
 * Touches come from the card's Compose pointer input, not from this View, so drawing gestures keep working.
 */
class JuiceLiquidView internal constructor(
    context: Context,
    private val random: Random,
    private val runner: LiquidRunner,
) : View(context), SensorEventListener {

    constructor(context: Context) : this(context, Random.Default, LiquidRunner.shared)

    /** Fill level, 0 (thirsty) to 1 (satiated, the card full). New juice streams in; drained juice leaves from the surface. */
    var fill = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v == field) return
            field = v
            onEngine { it.fillTo(v); applySparkle(it, v); startLoop() }
        }

    /** Particle size and shading resolution. Set on every recomposition, so a no-op unless it changes. */
    var quality = LiquidQuality.BALANCED
        set(value) {
            if (value == field) return
            field = value
            val f = fill
            onEngine { it.setQuality(value, f); publish(it) }
            layoutField(); shaderDirty = true; invalidate()
        }

    /** Corner radius of the glass card, in dp. */
    var glassCornerDp = 30f
        set(value) { field = value; onEngine { it.sim.p.corner = value - 4f }; updateClip(); shaderDirty = true; invalidate() }

    /** Golden Honey: thicker juice that settles faster. */
    var viscous = false
        set(value) { field = value; onEngine { applyViscosity(it, value) } }

    /** Carbonated flavours grow bubbles when shaken; still ones only foam. */
    var fizz = true
        set(value) { field = value; onEngine { it.froth.carbonated = value } }

    /** Reduce motion turns this off: no tilt, shake or touch, the juice rests on the bottom. Set on every recomposition, so a no-op unless it changes. */
    var sloshEnabled = true
        set(value) { if (value == field) return; field = value; onEngine { it.motion = value; startLoop() } }

    /** Foam, bubbles and pops. */
    var particlesEnabled = true
        set(value) { if (value == field) return; field = value; onEngine { it.frothEnabled = value; publish(it) } }

    private val density = resources.displayMetrics.density

    /** Only touched on [runner]'s thread. */
    internal var engine: LiquidEngine? = null
        private set
    private val frames = LiquidFrames()

    private var accent = Color.parseColor("#FFB020")
    private var deep = Color.parseColor("#E8590C")
    private val cream = Color.parseColor("#FFF8EC")

    // ---------- rendering resources ----------
    /** The particle field, drawn at the quality's field scale and shaded there by the AGSL effect. */
    private val fieldNode = RenderNode("juice-field")
    /** A layer the size of the field, so the shader runs at that resolution before it is scaled up to the card. */
    private val liquidNode = RenderNode("juice-liquid")

    /** Null when the AGSL fails to compile; [onDraw] then draws plain dots. */
    private val shader: RuntimeShader? = try {
        RuntimeShader(LiquidShaders.COMPOSITE)
    } catch (e: RuntimeException) {
        Log.e(TAG, "Liquid shader failed to compile; drawing plain dots", e)
        null
    }
    private var shaderDirty = true
    private val kernel = kernelBitmap()
    /** Every particle in one draw: white falloff texture, tinted per vertex (R juice, G speed, B foam), added up. */
    private val stamp = Paint().apply {
        blendMode = BlendMode.PLUS; isFilterBitmap = true
        shader = BitmapShader(kernel, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val cells = Paint().apply { isFilterBitmap = true }
    private val sprite = Paint().apply { isFilterBitmap = true }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val speck = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleBmp = bubbleBitmap()
    private var cellBmp = foamCellBitmap().also { cells.shader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val rect = RectF()
    private val clip = Path()

    private val stampBatch = QuadBatch(LiquidSim.CAPACITY, KERNEL_PX.toFloat())
    private val cellBatch = QuadBatch(LiquidSim.CAPACITY * 3, CELL_PX.toFloat())

    // three foam cells per particle, with fixed offsets and sizes
    private val cellOx = FloatArray(LiquidSim.CAPACITY * 3)
    private val cellOy = FloatArray(LiquidSim.CAPACITY * 3)
    private val cellSize = FloatArray(LiquidSim.CAPACITY * 3)

    init {
        val r = Random(7)
        for (i in cellOx.indices) {
            cellOx[i] = (r.nextFloat() - 0.5f) * 14f; cellOy[i] = (r.nextFloat() - 0.5f) * 14f
            val h = r.nextFloat(); cellSize[i] = 4f + h * h * 11f
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Runs [block] with the engine on its thread, once there is one. */
    private fun onEngine(block: (LiquidEngine) -> Unit) = runner.post { engine?.let(block) }

    /** On the engine's thread: hands the engine's state to the UI and asks for a draw. */
    private fun publish(e: LiquidEngine) { frames.publish(e); postInvalidateOnAnimation() }

    /** Sets the drink colours (ARGB). Accent is the main juice colour, deep is the darker tone in thick areas. */
    fun setFlavour(@ColorInt accentColor: Int, @ColorInt deepColor: Int) {
        if (accentColor == accent && deepColor == deep) return
        accent = accentColor; deep = deepColor
        cellBmp = foamCellBitmap()
        cells.shader = BitmapShader(cellBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shaderDirty = true
        invalidate()
    }

    /** Sloshes the juice sideways, e.g. on a carousel snap (signed by direction) or a completion. */
    fun kick(velocity: Float) = onEngine { it.kick(velocity); startLoop() }

    /** A burst of froth: [count] is how big, about 40 for reaching Satiated and 10 for a completion. */
    fun emitParticles(count: Int = 40) = onEngine { it.splash(count * SPLASH_PER_PARTICLE); startLoop() }

    /** A finger on the card at ([x], [y]), both 0..1 across the view. */
    fun touchDown(x: Float, y: Float) {
        val tx = x * width / density; val ty = y * height / density
        onEngine { it.touchDown(tx, ty); startLoop() }
    }
    fun touchMove(x: Float, y: Float) {
        val tx = x * width / density; val ty = y * height / density
        onEngine { it.touchMove(tx, ty) }
    }
    fun touchUp() = onEngine { it.touchUp() }

    private fun applySparkle(e: LiquidEngine, fill: Float) { e.froth.sparkle = if (fill > SATIATED_FILL) SATIATED_SPARKLE else 0f }

    private fun applyViscosity(e: LiquidEngine, viscous: Boolean) {
        e.sim.p.xsph = if (viscous) HONEY_XSPH else WATER_XSPH
        e.sim.p.damp = if (viscous) HONEY_DAMP else WATER_DAMP
    }

    // ---------- size ----------
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val wd = w / density; val hd = h / density
        val corner = glassCornerDp; val motion = sloshEnabled; val froth = particlesEnabled; val carbonated = fizz
        val thick = viscous; val q = quality; val f = fill
        runner.post {
            val e = engine
            if (e == null) {
                val created = LiquidEngine(wd, hd, random).apply {
                    sim.p.corner = corner - 4f
                    sim.setRadius(q.h)
                    fingerRadius = max(26f, wd * 0.09f)
                    this.motion = motion
                    frothEnabled = froth
                    this.froth.carbonated = carbonated
                }
                applyViscosity(created, thick)
                applySparkle(created, f)
                engine = created
                // The first pour is already resting, so opening the overlay never shows the glass filling up.
                created.fill(f)
                publish(created)
            } else { e.resize(wd, hd); publish(e) }
            startLoop()
        }
        layoutField()
        updateClip()
        shaderDirty = true
    }

    private fun fieldScale() = quality.fieldScale

    private fun layoutField() {
        if (width <= 0 || height <= 0) return
        val fw = ceil(width * fieldScale()).toInt(); val fh = ceil(height * fieldScale()).toInt()
        fieldNode.setPosition(0, 0, fw, fh)
        liquidNode.setPosition(0, 0, fw, fh)
        // At full resolution there is nothing to scale up, so skip the extra layer.
        liquidNode.setUseCompositingLayer(fieldScale() < 1f, null)
    }

    private fun updateClip() {
        clip.reset()
        clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), glassCornerDp * density, glassCornerDp * density, Path.Direction.CW)
    }

    // ---------- frame loop (fixed 60 Hz sim on the engine's thread, stops when asleep) ----------
    @Volatile private var running = false
    private var lastNanos = 0L
    private var accumulatorMs = 0f
    private val stats = LiquidStats()
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            val e = engine ?: run { running = false; return }
            if (!running) return
            if (lastNanos != 0L) accumulatorMs += min(100f, (now - lastNanos) / 1e6f)
            lastNanos = now
            var steps = 0; var changed = false
            val t0 = System.nanoTime()
            while (accumulatorMs >= STEP_MS && steps < 3) { changed = e.tick() or changed; accumulatorMs -= STEP_MS; steps++ }
            if (steps > 0) stats.stepped(steps, System.nanoTime() - t0, e.sim.count)
            if (steps == 3) accumulatorMs = 0f
            if (changed) publish(e)
            if (e.sleeping) { running = false; lastNanos = 0L; accumulatorMs = 0f }
            else runner.postFrame(this)
        }
    }

    /** Whether the frame loop is posted; it stops once the juice settles. */
    internal val animating get() = running

    /** On the engine's thread. */
    private fun startLoop() {
        val e = engine ?: return
        if (running || !active) return
        e.wake()
        running = true; lastNanos = 0L
        runner.postFrame(frame)
    }

    /** On the engine's thread. */
    private fun stopLoop() { runner.removeFrame(frame); running = false }

    // ---------- visibility: sensors and loop only run while shown ----------
    /** Only touched on the engine's thread. */
    private var active = false
    private var shown = false
    private val sensors = context.getSystemService(SensorManager::class.java)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        setShown(isVisible)
    }

    override fun onDetachedFromWindow() {
        setShown(false)
        super.onDetachedFromWindow()
    }

    private fun setShown(on: Boolean) {
        if (on == shown) return
        shown = on
        if (on) {
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, runner.handler)
            }
        } else sensors?.unregisterListener(this)
        runner.post {
            active = on
            if (on) startLoop() else { haveMotion = false; stopLoop() }
        }
    }

    // ---------- accelerometer (delivered on the engine's thread) ----------
    private var smGx = 0f; private var smGy = 1f; private var haveMotion = false

    override fun onSensorChanged(event: SensorEvent) {
        onAcceleration(event.values[0], event.values[1], event.values[2])
    }

    /** An accelerometer reading in m/s², device axes. On the engine's thread. */
    internal fun onAcceleration(ax: Float, ay: Float, az: Float) {
        val e = engine ?: return
        val quarterTurns = when (display?.rotation) {
            Surface.ROTATION_90 -> 1; Surface.ROTATION_180 -> 2; Surface.ROTATION_270 -> 3; else -> 0
        }
        val g = ScreenGravity.of(ax, ay, az, quarterTurns)
        if (haveMotion) { smGx = smGx * 0.35f + g.x * 0.65f; smGy = smGy * 0.35f + g.y * 0.65f }
        else { smGx = g.x; smGy = g.y; haveMotion = true }
        e.setGravity(smGx, smGy, g.z)
        if (!running && (e.wantsToWake() || !e.sleeping)) startLoop()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---------- drawing (UI thread, from the newest frame) ----------
    override fun onDraw(canvas: Canvas) {
        val f = frames.latest()
        val composite = shader
        if (composite == null || !canvas.isHardwareAccelerated) { drawFallback(canvas, f); return }
        val t0 = System.nanoTime()
        val fs = fieldScale()
        val scale = density * fs

        // 1. every particle into the field in one draw (additive: R juice, G speed, B foam)
        stampBatch.clear()
        val kernelDp = KERNEL_DP * f.h / LAB_H
        for (i in 0 until f.count) {
            val foam = f.foam[i]; val juice = 1f - foam
            val rad = kernelDp * (1f + 0.35f * foam)
            val r = (0.3f * juice * 255f).roundToInt()
            val g = (min(f.speed[i], 5f) * 0.05f * juice * 255f).roundToInt()
            val b = (0.5f * foam * 255f).roundToInt()
            stampBatch.add(f.x[i], f.y[i], rad, Color.rgb(r, g, b))
        }
        val rc = fieldNode.beginRecording()
        rc.scale(scale, scale)
        stampBatch.draw(rc, stamp)
        fieldNode.endRecording()

        // 2. threshold and shade with AGSL, at the field's resolution
        if (shaderDirty) {
            composite.setFloatUniform("size", fieldNode.width.toFloat(), fieldNode.height.toFloat())
            composite.setFloatUniform("px", scale)
            composite.setFloatUniform("corner", glassCornerDp)
            setColor(composite, "acc", accent); setColor(composite, "deep", deep); setColor(composite, "cream", cream)
            fieldNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(composite, "field"))
            shaderDirty = false
        }
        val lc = liquidNode.beginRecording()
        lc.drawRenderNode(fieldNode)
        liquidNode.endRecording()

        canvas.save()
        canvas.clipPath(clip)
        canvas.save()
        canvas.scale(1f / fs, 1f / fs)
        canvas.drawRenderNode(liquidNode)
        canvas.restore()

        canvas.scale(density, density)
        if (f.froth) drawFroth(canvas, f)
        canvas.restore()
        stats.drew(System.nanoTime() - t0, quality)
    }

    private fun drawFroth(canvas: Canvas, f: LiquidFrame) {
        // 3. foam cells riding on foam particles, in one draw
        cellBatch.clear()
        for (i in 0 until f.count) {
            val foam = f.foam[i]
            if (foam < 0.12f) continue
            val a = (smooth(0.12f, 0.5f, foam) * 255f).roundToInt()
            val grow = 0.55f + 0.45f * foam
            for (k in 0 until 3) {
                val c = i * 3 + k
                cellBatch.add(f.x[i] + cellOx[c], f.y[i] + cellOy[c], cellSize[c] * grow * 0.5f, Color.argb(a, 255, 255, 255))
            }
        }
        cellBatch.draw(canvas, cells)
        // 4. rising bubbles
        sprite.alpha = 255
        for (k in 0 until f.bubbles) {
            val age = f.bubbleAge[k]; val st = f.bubbleStretch[k]
            val r = f.bubbleR[k] * min(1f, age / 8f) * (1f + sin(age * 0.5f + f.bubblePh[k]) * 0.06f)
            val deg = Math.toDegrees(f.bubbleAng[k].toDouble()).toFloat()
            canvas.save()
            canvas.translate(f.bubbleX[k], f.bubbleY[k]); canvas.rotate(deg); canvas.scale(1f + st, 1f - st * 0.6f); canvas.rotate(-deg)
            rect.set(-r, -r, r, r); canvas.drawBitmap(bubbleBmp, null, rect, sprite)
            canvas.restore()
        }
        // 5. pops: an expanding ring and a little spray
        for (k in 0 until f.pops) {
            val t = f.popT[k] / Froth.POP_FRAMES; val ease = 1 - (1 - t) * (1 - t); val pr = f.popR[k]
            ring.color = withAlpha(cream, (1 - t) * 0.85f)
            canvas.drawCircle(f.popX[k], f.popY[k], pr * (1 + ease * 1.8f), ring)
            if (pr > 2f) {
                speck.color = withAlpha(Color.WHITE, (1 - t) * 0.8f)
                for (s in 0 until 4) {
                    val a = f.popDir[k] + s * 1.57f; val dd = pr * (1.2f + ease * 2.6f)
                    canvas.drawCircle(f.popX[k] + cos(a) * dd, f.popY[k] + sin(a) * dd, 0.65f, speck)
                }
            }
        }
    }

    private fun drawFallback(canvas: Canvas, f: LiquidFrame) {
        speck.color = withAlpha(accent, 0.9f)
        for (i in 0 until f.count) canvas.drawCircle(f.x[i] * density, f.y[i] * density, FALLBACK_DOT_DP * density, speck)
    }

    private fun setColor(s: RuntimeShader, name: String, c: Int) =
        s.setFloatUniform(name, Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    // ---------- sprites, generated once ----------
    /** Soft falloff (1 - d^2)^2 in white; each particle's vertex colour tints it. */
    private fun kernelBitmap(): Bitmap {
        val n = KERNEL_PX; val px = IntArray(n * n)
        for (j in 0 until n) for (i in 0 until n) {
            val u = (i + 0.5f) / n * 2 - 1; val v = (j + 0.5f) / n * 2 - 1
            val dd = u * u + v * v
            val w = if (dd >= 1f) 0f else (1 - dd) * (1 - dd)
            px[j * n + i] = Color.argb((w * 255f).roundToInt(), 255, 255, 255)
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    private fun bubbleBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(32f, 32f, 31f,
            intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(36, 255, 255, 255),
                Color.argb(191, 255, 248, 236), Color.argb(0, 255, 248, 236)),
            floatArrayOf(0f, 0.26f, 0.78f, 0.93f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 31f, p)
        p.shader = null
        p.color = Color.argb(242, 255, 255, 255)
        c.save(); c.rotate(-40f, 22f, 21f); c.drawOval(15f, 16.5f, 29f, 25.5f, p); c.restore()
        p.color = Color.argb(128, 255, 255, 255); c.drawCircle(42f, 44f, 2.5f, p)
        return bmp
    }

    /** A soap-bubble cell: cream body, tinted rim, bright highlight. */
    private fun foamCellBitmap(): Bitmap {
        val n = CELL_PX; val px = IntArray(n * n)
        val cr = Color.red(cream) / 255f; val cg = Color.green(cream) / 255f; val cb = Color.blue(cream) / 255f
        val ar = Color.red(accent) / 255f; val ag = Color.green(accent) / 255f; val ab = Color.blue(accent) / 255f
        val dr = Color.red(deep) / 255f; val dg = Color.green(deep) / 255f; val db = Color.blue(deep) / 255f
        for (j in 0 until n) for (i in 0 until n) {
            val u = (i + 0.5f) / n * 2 - 1; val v = (j + 0.5f) / n * 2 - 1
            val d = sqrt(u * u + v * v)
            if (d > 1f) { px[j * n + i] = 0; continue }
            val edge = smooth(0.62f, 0.92f, d) * (1 - smooth(0.92f, 1f, d))
            val hi = 1 - smooth(0f, 0.38f, hypot(u + 0.34f, v + 0.36f))
            val lo = (1 - smooth(0f, 0.3f, hypot(u - 0.38f, v - 0.42f))) * 0.5f
            fun ch(c: Float, a: Float, dk: Float): Float {
                val rim = c + (dk - c) * 0.35f
                val tint = c + (a - c) * 0.4f
                return min(1f, c * 0.96f * (1 - edge) + rim * edge + hi * 0.5f + tint * lo * 0.3f)
            }
            val alpha = min(1f, 0.5f + edge * 0.45f + hi * 0.4f)
            px[j * n + i] = Color.argb((alpha * 255).roundToInt(), (ch(cr, ar, dr) * 255).roundToInt(),
                (ch(cg, ag, dg) * 255).roundToInt(), (ch(cb, ab, db) * 255).roundToInt())
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    private fun withAlpha(c: Int, a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(c), Color.green(c), Color.blue(c))

    private companion object {
        const val TAG = "JuiceLiquidView"
        const val STEP_MS = 1000f / 60f
        /** Blob radius at the lab's interaction radius [LAB_H]; it scales with the sim's. */
        const val KERNEL_DP = 15f
        const val LAB_H = 26f
        const val KERNEL_PX = 64
        const val CELL_PX = 48
        const val FALLBACK_DOT_DP = 6f
        const val SATIATED_FILL = 0.75f
        /** Chance per frame of a bubble rising on its own while Satiated. */
        const val SATIATED_SPARKLE = 0.25f
        const val SPLASH_PER_PARTICLE = 0.04f
        const val WATER_XSPH = 0.05f
        const val WATER_DAMP = 0.006f
        const val HONEY_XSPH = 0.22f
        const val HONEY_DAMP = 0.012f
    }
}
