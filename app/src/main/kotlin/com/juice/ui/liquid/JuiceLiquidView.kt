package com.juice.ui.liquid

import android.content.Context
import android.graphics.Bitmap
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
import com.juice.core.liquid.JuiceBody
import com.juice.core.liquid.LiquidEngine
import com.juice.core.liquid.LiquidSim
import com.juice.core.liquid.ScreenGravity
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
 * Touches come from the card's Compose pointer input, not from this View, so drawing gestures keep working.
 */
class JuiceLiquidView(context: Context, private val random: Random = Random.Default) : View(context), SensorEventListener {

    /** Fill level, 0 (thirsty) to 1 (satiated, the card full). New juice streams in; drained juice leaves from the surface. */
    var fill = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v == field) return
            field = v
            engine?.let { it.fillTo(v); applySparkle(); startLoop() }
        }

    /** Corner radius of the glass card, in dp. */
    var glassCornerDp = 30f
        set(value) { field = value; engine?.sim?.p?.corner = value - 4f; updateClip(); shaderDirty = true; invalidate() }

    /** Golden Honey: thicker juice that settles faster. */
    var viscous = false
        set(value) { field = value; engine?.let(::applyViscosity) }

    /** Carbonated flavours grow bubbles when shaken; still ones only foam. */
    var fizz = true
        set(value) { field = value; engine?.froth?.carbonated = value }

    /** Reduce motion turns this off: no tilt, shake or touch, the juice rests on the bottom. Set on every recomposition, so a no-op unless it changes. */
    var sloshEnabled = true
        set(value) { if (value == field) return; field = value; engine?.motion = value; startLoop() }

    /** Foam, bubbles and pops. */
    var particlesEnabled = true
        set(value) { if (value == field) return; field = value; engine?.frothEnabled = value; invalidate() }

    private val density = resources.displayMetrics.density
    internal var engine: LiquidEngine? = null
        private set

    private var accent = Color.parseColor("#FFB020")
    private var deep = Color.parseColor("#E8590C")
    private val cream = Color.parseColor("#FFF8EC")

    // ---------- rendering resources ----------
    private val fieldNode = RenderNode("juice-field")

    /** Null when the AGSL fails to compile; [onDraw] then draws plain dots. */
    private val shader: RuntimeShader? = try {
        RuntimeShader(LiquidShaders.COMPOSITE)
    } catch (e: RuntimeException) {
        Log.e(TAG, "Liquid shader failed to compile; drawing plain dots", e)
        null
    }
    private var shaderDirty = true
    private val stamp = Paint().apply { blendMode = BlendMode.PLUS; isFilterBitmap = true }
    private val sprite = Paint().apply { isFilterBitmap = true }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val speck = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kernelR = kernel(0); private val kernelG = kernel(1); private val kernelB = kernel(2)
    private val bubbleBmp = bubbleBitmap()
    private var cellBmp = foamCellBitmap()
    private val rect = RectF()
    private val clip = Path()
    private val bodyPath = Path()
    private val bodyPaint = Paint().apply { blendMode = BlendMode.PLUS; color = Color.RED }

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

    /** Sets the drink colours (ARGB). Accent is the main juice colour, deep is the darker tone in thick areas. */
    fun setFlavour(@ColorInt accentColor: Int, @ColorInt deepColor: Int) {
        if (accentColor == accent && deepColor == deep) return
        accent = accentColor; deep = deepColor
        cellBmp = foamCellBitmap(); shaderDirty = true
        invalidate()
    }

    /** Sloshes the juice sideways, e.g. on a carousel snap (signed by direction) or a completion. */
    fun kick(velocity: Float) { engine?.kick(velocity); startLoop() }

    /** A burst of froth: [count] is how big, about 40 for reaching Satiated and 10 for a completion. */
    fun emitParticles(count: Int = 40) { engine?.splash(count * SPLASH_PER_PARTICLE); startLoop() }

    /** A finger on the card at ([x], [y]), both 0..1 across the view. */
    fun touchDown(x: Float, y: Float) { engine?.touchDown(x * width / density, y * height / density); startLoop() }
    fun touchMove(x: Float, y: Float) { engine?.touchMove(x * width / density, y * height / density) }
    fun touchUp() { engine?.touchUp() }

    private fun applySparkle() { engine?.froth?.sparkle = if (fill > SATIATED_FILL) SATIATED_SPARKLE else 0f }

    private fun applyViscosity(e: LiquidEngine) {
        e.sim.p.xsph = if (viscous) HONEY_XSPH else WATER_XSPH
        e.sim.p.damp = if (viscous) HONEY_DAMP else WATER_DAMP
    }

    // ---------- size ----------
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val wd = w / density; val hd = h / density
        val e = engine
        if (e == null) {
            val created = LiquidEngine(wd, hd, random).apply {
                sim.p.corner = glassCornerDp - 4f
                fingerRadius = max(26f, wd * 0.09f)
                motion = sloshEnabled
                frothEnabled = particlesEnabled
                froth.carbonated = fizz
            }
            applyViscosity(created)
            engine = created
            applySparkle()
            // The first pour is already resting, so opening the overlay never shows the glass filling up.
            created.fill(fill)
        } else e.resize(wd, hd)
        fieldNode.setPosition(0, 0, w, h)
        updateClip()
        shaderDirty = true
        startLoop()
    }

    private fun updateClip() {
        clip.reset()
        clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), glassCornerDp * density, glassCornerDp * density, Path.Direction.CW)
    }

    // ---------- frame loop (fixed 60 Hz sim, stops when asleep) ----------
    private var running = false
    private var lastNanos = 0L
    private var accumulatorMs = 0f
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            val e = engine ?: run { running = false; return }
            if (lastNanos != 0L) accumulatorMs += min(100f, (now - lastNanos) / 1e6f)
            lastNanos = now
            var steps = 0; var changed = false
            while (accumulatorMs >= STEP_MS && steps < 3) { changed = e.tick() or changed; accumulatorMs -= STEP_MS; steps++ }
            if (steps == 3) accumulatorMs = 0f
            if (changed) invalidate()
            if (e.sleeping) { running = false; lastNanos = 0L; accumulatorMs = 0f }
            else Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Whether the frame loop is posted; it stops once the juice settles. */
    internal val animating get() = running

    private fun startLoop() {
        val e = engine ?: return
        if (running || !active) return
        e.wake()
        running = true; lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stopLoop() { Choreographer.getInstance().removeFrameCallback(frame); running = false }

    // ---------- visibility: sensors and loop only run while shown ----------
    private var active = false
    private val sensors = context.getSystemService(SensorManager::class.java)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        setActive(isVisible)
    }

    override fun onDetachedFromWindow() {
        setActive(false)
        super.onDetachedFromWindow()
    }

    private fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        if (on) {
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            startLoop()
        } else {
            sensors?.unregisterListener(this)
            haveMotion = false
            stopLoop()
        }
    }

    // ---------- accelerometer ----------
    private var smGx = 0f; private var smGy = 1f; private var haveMotion = false

    override fun onSensorChanged(event: SensorEvent) {
        onAcceleration(event.values[0], event.values[1], event.values[2])
    }

    /** An accelerometer reading in m/s², device axes. */
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

    // ---------- drawing ----------
    override fun onDraw(canvas: Canvas) {
        val e = engine ?: return
        val sim = e.sim
        val composite = shader
        if (composite == null || !canvas.isHardwareAccelerated) { drawFallback(canvas, e); return }
        val d = density

        // 1. stamp every particle into the field (additive: R juice, G speed, B foam)
        val rc = fieldNode.beginRecording(width, height)
        rc.scale(d, d)
        for (i in 0 until sim.count) {
            val f = sim.foam[i]
            val rad = KERNEL_DP * (1f + 0.35f * f)
            rect.set(sim.x[i] - rad, sim.y[i] - rad, sim.x[i] + rad, sim.y[i] + rad)
            val juice = 1f - f
            if (juice > 0.004f) {
                stamp.alpha = (0.3f * juice * 255f).roundToInt(); rc.drawBitmap(kernelR, null, rect, stamp)
                val sp = min(hypot(sim.vx[i], sim.vy[i]), 5f) * 0.05f * juice
                if (sp > 0.004f) { stamp.alpha = (sp * 255f).roundToInt(); rc.drawBitmap(kernelG, null, rect, stamp) }
            }
            if (f > 0.004f) { stamp.alpha = (0.5f * f * 255f).roundToInt(); rc.drawBitmap(kernelB, null, rect, stamp) }
        }
        // the solid body under the particles, full strength juice
        if (bodyPath(e)) rc.drawPath(bodyPath, bodyPaint)
        fieldNode.endRecording()

        // 2. threshold and shade with AGSL
        if (shaderDirty) {
            composite.setFloatUniform("size", width.toFloat(), height.toFloat())
            composite.setFloatUniform("px", d)
            composite.setFloatUniform("corner", glassCornerDp)
            setColor(composite, "acc", accent); setColor(composite, "deep", deep); setColor(composite, "cream", cream)
            fieldNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(composite, "field"))
            shaderDirty = false
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRenderNode(fieldNode)

        canvas.scale(d, d)
        if (e.frothEnabled) drawFroth(canvas, e)
        canvas.restore()
    }

    private fun drawFroth(canvas: Canvas, e: LiquidEngine) {
        val sim = e.sim
        // 3. foam cells riding on foam particles
        for (i in 0 until sim.count) {
            val f = sim.foam[i]
            if (f < 0.12f) continue
            sprite.alpha = (smooth(0.12f, 0.5f, f) * 255f).roundToInt()
            val grow = 0.55f + 0.45f * f
            for (k in 0 until 3) {
                val c = i * 3 + k
                val r = cellSize[c] * grow * 0.5f
                val cx = sim.x[i] + cellOx[c]; val cy = sim.y[i] + cellOy[c]
                rect.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawBitmap(cellBmp, null, rect, sprite)
            }
        }
        // 4. rising bubbles
        sprite.alpha = 255
        for (b in e.froth.bubbles) {
            val x = e.froth.bubbleX(b); val y = e.froth.bubbleY(b)
            val r = b.r * min(1f, b.age / 8f) * (1f + sin(b.age * 0.5f + b.ph) * 0.06f)
            val deg = Math.toDegrees(b.ang.toDouble()).toFloat()
            canvas.save()
            canvas.translate(x, y); canvas.rotate(deg); canvas.scale(1f + b.stretch, 1f - b.stretch * 0.6f); canvas.rotate(-deg)
            rect.set(-r, -r, r, r); canvas.drawBitmap(bubbleBmp, null, rect, sprite)
            canvas.restore()
        }
        // 5. pops: an expanding ring and a little spray
        for (p in e.froth.pops) {
            val k = p.t / Froth.POP_FRAMES.toFloat(); val ease = 1 - (1 - k) * (1 - k)
            ring.color = withAlpha(cream, (1 - k) * 0.85f)
            canvas.drawCircle(p.x, p.y, p.r * (1 + ease * 1.8f), ring)
            if (p.r > 2f) {
                speck.color = withAlpha(Color.WHITE, (1 - k) * 0.8f)
                for (s in 0 until 4) {
                    val a = p.dir + s * 1.57f; val dd = p.r * (1.2f + ease * 2.6f)
                    canvas.drawCircle(p.x + cos(a) * dd, p.y + sin(a) * dd, 0.65f, speck)
                }
            }
        }
    }

    /** Fills [bodyPath] (in dp) with the engine's solid body; false when there is none. */
    private fun bodyPath(e: LiquidEngine): Boolean {
        if (e.body <= 0f) return false
        val poly = JuiceBody.polygon(e.bodyNx, e.bodyNy, e.sim.floorLevel, e.sim.width, e.sim.height)
        if (poly.isEmpty()) return false
        bodyPath.rewind()
        bodyPath.moveTo(poly[0], poly[1])
        for (i in 1 until poly.size / 2) bodyPath.lineTo(poly[2 * i], poly[2 * i + 1])
        bodyPath.close()
        return true
    }

    private fun drawFallback(canvas: Canvas, e: LiquidEngine) {
        val sim = e.sim
        speck.color = withAlpha(accent, 0.9f)
        if (bodyPath(e)) {
            canvas.save(); canvas.clipPath(clip); canvas.scale(density, density)
            canvas.drawPath(bodyPath, speck)
            canvas.restore()
        }
        for (i in 0 until sim.count) canvas.drawCircle(sim.x[i] * density, sim.y[i] * density, FALLBACK_DOT_DP * density, speck)
    }

    private fun setColor(s: RuntimeShader, name: String, c: Int) =
        s.setFloatUniform(name, Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    // ---------- sprites, generated once ----------
    /** Soft falloff (1 - d^2)^2 in a single colour channel. */
    private fun kernel(channel: Int): Bitmap {
        val n = 64; val px = IntArray(n * n)
        for (j in 0 until n) for (i in 0 until n) {
            val u = (i + 0.5f) / n * 2 - 1; val v = (j + 0.5f) / n * 2 - 1
            val dd = u * u + v * v
            val w = if (dd >= 1f) 0f else (1 - dd) * (1 - dd)
            val a = (w * 255f).roundToInt()
            px[j * n + i] = Color.argb(a, if (channel == 0) 255 else 0, if (channel == 1) 255 else 0, if (channel == 2) 255 else 0)
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
        val n = 48; val px = IntArray(n * n)
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
        const val KERNEL_DP = 15f
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
