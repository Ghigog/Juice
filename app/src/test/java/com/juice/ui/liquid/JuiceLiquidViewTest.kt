package com.juice.ui.liquid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.juice.core.liquid.LiquidEngine
import com.juice.core.liquid.ScreenGravity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class JuiceLiquidViewTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun view(fill: Float = 0.5f, configure: JuiceLiquidView.() -> Unit = {}) =
        JuiceLiquidView(context, Random(1)).apply {
            this.fill = fill
            configure()
            measure(View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY))
            layout(0, 0, 340, 440)
        }

    private val JuiceLiquidView.dp get() = resources.displayMetrics.density
    private fun JuiceLiquidView.expected(fill: Float) = LiquidEngine.particlesFor(fill, width / dp, height / dp)

    @Test fun thereIsNoJuiceUntilTheViewHasASize() {
        assertNull(JuiceLiquidView(context, Random(1)).apply { fill = 1f }.engine)
    }

    @Test fun theFirstPourHoldsTheMeterAlreadyResting() {
        val v = view(0.5f)
        val e = assertNotNull(v.engine)
        assertEquals(v.expected(0.5f), e.sim.count)
        assertEquals(0, e.pending)
    }

    @Test fun aFullerMeterStreamsJuiceInAndAnEmptierOneDrainsIt() {
        val v = view(0.1f)
        val e = v.engine!!
        v.fill = 0.2f
        assertEquals(v.expected(0.2f), e.target)
        repeat(200) { e.tick() }
        assertEquals(v.expected(0.2f), e.sim.count)
        v.fill = 0.05f
        assertEquals(v.expected(0.05f), e.sim.count)
        v.fill = 7f
        assertEquals(1f, v.fill)
        assertEquals(v.expected(1f), e.target)
    }

    @Test fun satiatedJuiceSparkles() {
        val v = view(0.5f)
        assertEquals(0f, v.engine!!.froth.sparkle)
        v.fill = 0.9f
        assertTrue(v.engine!!.froth.sparkle > 0f)
    }

    @Test fun settingsReachTheEngine() {
        val v = view { sloshEnabled = false; particlesEnabled = false; fizz = false; viscous = true }
        val e = v.engine!!
        assertFalse(e.motion)
        assertFalse(e.frothEnabled)
        assertFalse(e.froth.carbonated)
        val thick = e.sim.p.xsph
        v.viscous = false
        assertTrue(e.sim.p.xsph < thick)
        v.sloshEnabled = true; v.particlesEnabled = true; v.fizz = true
        assertTrue(e.motion && e.frothEnabled && e.froth.carbonated)
    }

    @Test fun touchesArriveInDpAcrossTheView() {
        val v = view()
        v.touchDown(0.5f, 0.25f)
        val e = v.engine!!
        assertTrue(e.isTouching)
        assertEquals(170f / v.dp, e.fingerX, 1e-3f)
        assertEquals(110f / v.dp, e.fingerY, 1e-3f)
        v.touchMove(0.6f, 0.25f)
        e.tick()
        assertEquals(204f / v.dp, e.fingerX, 1e-3f)
        v.touchUp()
        assertFalse(e.isTouching)
    }

    @Test fun theAccelerometerSetsGravity() {
        val v = view()
        v.onAcceleration(-ScreenGravity.EARTH, 0f, 0f)
        assertEquals(1f, v.engine!!.gx, 1e-4f)
        // Smoothed: the next reading moves gravity most of the way, not all of it.
        v.onAcceleration(0f, -ScreenGravity.EARTH, 0f)
        assertTrue(v.engine!!.gy < 0f && v.engine!!.gy > -1f)
    }

    @Test fun kicksAndSplashesStirTheJuice() {
        val v = view()
        val e = v.engine!!
        repeat(600) { e.tick() }
        assertTrue(e.sleeping)
        v.kick(3f)
        assertFalse(e.sleeping)
        repeat(600) { e.tick() }
        v.emitParticles()
        assertTrue(e.froth.agitation > 1f)
    }

    @Test fun aFullMeterStartsWithTheCardFull() {
        val e = view(1f).engine!!
        assertEquals(LiquidEngine.particlesFor(1f, e.sim.width, e.sim.height), e.sim.count)
        assertEquals(0, e.pending)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun aSoftwareCanvasDrawsTheJuiceAsDots() {
        val v = view(0.2f)
        v.setFlavour(Color.RED, Color.BLACK)
        val bmp = Bitmap.createBitmap(340, 440, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val e = v.engine!!
        val x = (e.sim.x[0] * v.dp).toInt(); val y = (e.sim.y[0] * v.dp).toInt()
        assertEquals(Color.RED, bmp.getPixel(x, y) or 0xFF000000.toInt())
    }

    @Test fun theLoopOnlyRunsWhileShown() {
        val v = view()
        assertFalse(v.animating)
        v.onVisibilityAggregated(true)
        assertTrue(v.animating)
        v.onVisibilityAggregated(false)
        assertFalse(v.animating)
    }
}
