package com.juice.core.liquid

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidEngineTest {
    private fun engine(seed: Int = 1, particles: Int = 200) =
        LiquidEngine(200f, 260f, Random(seed)).apply { pour(particles) }

    private fun LiquidEngine.run(frames: Int) = repeat(frames) { tick() }

    private fun LiquidEngine.meanX() = (0 until sim.count).map { sim.x[it] }.average().toFloat()
    private fun LiquidEngine.meanY() = (0 until sim.count).map { sim.y[it] }.average().toFloat()

    /** Shakes the phone side to side for [frames] frames. */
    private fun LiquidEngine.shake(frames: Int) = repeat(frames) {
        setGravity(if (it % 6 < 3) 1.6f else -1.6f, 1f, 0f); tick()
    }

    private fun LiquidEngine.allInsideTheGlass() = (0 until sim.count).all {
        sim.x[it] in 0f..sim.width && sim.y[it] in 0f..sim.height
    }

    @Test fun juiceRestsOnTheBottomAndStaysInTheGlass() {
        val e = engine()
        e.run(240)
        assertTrue(e.allInsideTheGlass())
        assertTrue(e.meanY() > e.sim.height * 0.6f)
    }

    @Test fun tiltingThePhoneRunsTheJuiceToTheLowSide() {
        val e = engine()
        e.setGravity(0.8f, 0.6f, 0f)
        e.run(240)
        assertTrue(e.meanX() > e.sim.width * 0.6f)
        assertTrue(e.allInsideTheGlass())
    }

    @Test fun settledJuiceSleepsAndATiltWakesIt() {
        val e = engine()
        e.run(600)
        assertTrue(e.sleeping)
        assertFalse(e.tick())
        e.setGravity(0.5f, 0.85f, 0f)
        assertTrue(e.wantsToWake())
        assertTrue(e.tick())
        assertFalse(e.sleeping)
    }

    @Test fun aFullMeterFillsTheGlass() {
        val e = LiquidEngine(340f, 440f, Random(2)).apply { fill(1f) }
        e.run(300)
        assertTrue(e.allInsideTheGlass())
        val top = (0 until e.sim.count).map { e.sim.y[it] }.sorted()[e.sim.count / 100]
        assertTrue(top < 440f * 0.08f, "surface at $top")
    }

    @Test fun deepJuiceHoldsStillOnceNothingMovesIt() {
        // Juice, then Golden Honey's thicker tuning.
        for ((xsph, damp) in listOf(0.05f to 0.006f, 0.22f to 0.012f)) {
            val e = LiquidEngine(340f, 440f, Random(2))
            e.sim.p.xsph = xsph; e.sim.p.damp = damp
            e.fill(0.8f)
            e.run(LiquidEngine.IDLE_FRAMES + 2)
            assertTrue(e.sleeping)
            assertFalse(e.tick())
        }
    }

    @Test fun anyInputPutsOffTheIdleRest() {
        val e = LiquidEngine(340f, 440f, Random(2)).apply { fill(0.8f) }
        e.run(LiquidEngine.IDLE_FRAMES - 10)
        e.setGravity(0.3f, 0.95f, 0f)
        e.run(LiquidEngine.IDLE_FRAMES - 10)
        assertFalse(e.sleeping)
        e.touchDown(100f, 300f)
        e.run(LiquidEngine.IDLE_FRAMES + 10)
        assertFalse(e.sleeping)
        e.touchUp()
        e.run(LiquidEngine.IDLE_FRAMES + 2)
        assertTrue(e.sleeping)
    }

    @Test fun aTouchWakesAndPushesTheJuice() {
        val e = engine()
        e.run(600)
        val before = e.meanX()
        e.touchDown(40f, e.meanY())
        assertTrue(e.isTouching)
        repeat(30) { e.touchMove(40f + it * 4f, e.meanY()); e.tick() }
        e.touchUp()
        assertFalse(e.isTouching)
        assertTrue(e.meanX() > before)
    }

    @Test fun shakingMakesFoamAndBubbles() {
        val e = engine()
        e.shake(120)
        assertTrue(e.froth.agitation > 0.5f)
        assertTrue(e.froth.foamLive > 0)
        assertTrue(e.froth.bubbles.isNotEmpty() || e.froth.pops.isNotEmpty())
    }

    @Test fun stillDrinksFoamButGrowNoBubbles() {
        val e = engine().apply { froth.carbonated = false }
        var bubbles = 0
        repeat(120) { e.shake(1); bubbles += e.froth.bubbles.size }
        assertEquals(0, bubbles)
        assertTrue(e.froth.foamLive > 0)
    }

    @Test fun frothOffMeansNoFoamBubblesOrPops() {
        val e = engine()
        e.shake(60)
        e.frothEnabled = false
        assertEquals(0, e.froth.bubbles.size + e.froth.pops.size)
        assertTrue((0 until e.sim.count).all { e.sim.foam[it] == 0f })
        e.shake(120)
        e.splash(3f)
        assertEquals(0, e.froth.bubbles.size + e.froth.pops.size + e.froth.foamLive)
    }

    @Test fun aSparklingDrinkBubblesUntilItRests() {
        val e = engine().apply { froth.sparkle = 0.5f }
        var seen = 0
        repeat(LiquidEngine.IDLE_FRAMES) { e.tick(); seen = maxOf(seen, e.froth.bubbles.size) }
        assertTrue(seen > 0)
        assertFalse(e.sleeping)
        e.run(10)
        assertTrue(e.sleeping)
    }

    @Test fun aSplashStirsUpFroth() {
        val e = engine()
        e.run(600)
        e.splash(2f)
        assertFalse(e.sleeping)
        e.run(30)
        assertTrue(e.froth.foamLive > 0)
    }

    @Test fun aKickSloshesTheJuiceAcrossGravity() {
        val e = engine()
        e.run(600)
        val before = e.meanX()
        e.kick(4f)
        e.run(8)
        assertTrue(e.meanX() > before + 2f)
        val left = engine().apply { run(600) }
        val start = left.meanX()
        left.kick(-4f)
        left.run(8)
        assertTrue(left.meanX() < start - 2f)
    }

    @Test fun reduceMotionHoldsGravityDownAndIgnoresInput() {
        val e = engine().apply { motion = false }
        e.setGravity(1f, 0f, 0f)
        assertEquals(0f, e.gx); assertEquals(1f, e.gy)
        e.touchDown(100f, 200f)
        assertFalse(e.isTouching)
        e.run(240)
        val x = e.meanX()
        e.kick(5f)
        e.run(4)
        assertEquals(x, e.meanX(), 0.5f)
        assertTrue(e.meanY() > e.sim.height * 0.6f)
        e.motion = true
        assertEquals(1f, e.gx); assertEquals(0f, e.gy)
    }

    @Test fun pouringStreamsJuiceInAFewParticlesAFrame() {
        val e = engine(particles = 0)
        e.pourTo(30)
        assertEquals(30, e.target)
        e.tick()
        assertEquals(LiquidEngine.POUR_PER_FRAME, e.sim.count)
        repeat(20) { e.tick() }
        assertEquals(30, e.sim.count)
        assertEquals(0, e.pending)
        e.run(240)
        assertTrue(e.allInsideTheGlass())
        assertTrue(e.meanY() > e.sim.height * 0.7f)
    }

    @Test fun pouringComesFromTheSideOppositeGravity() {
        val e = engine(particles = 0)
        e.setGravity(1f, 0f, 0f)
        e.pourTo(3)
        e.tick()
        assertTrue((0 until 3).all { e.sim.x[it] < e.sim.width / 2 })
    }

    @Test fun drainingTakesJuiceFromTheSurface() {
        val e = engine()
        e.run(240)
        val top = (0 until e.sim.count).minOf { e.sim.y[it] }
        e.pourTo(150)
        assertEquals(150, e.sim.count)
        assertTrue((0 until e.sim.count).all { e.sim.y[it] > top })
    }

    @Test fun pouringStopsAtCapacity() {
        val e = engine(particles = LiquidSim.CAPACITY - 2)
        e.pourTo(LiquidSim.CAPACITY + 50)
        e.tick()
        assertEquals(LiquidSim.CAPACITY, e.sim.count)
        assertEquals(0, e.pending)
    }

    @Test fun emptyGlassIsCalm() {
        val e = engine(particles = 0)
        e.shake(30)
        assertEquals(0, e.froth.foamLive)
        e.setGravity(0f, 1f, 0f)
        e.run(300)
        assertTrue(e.sleeping)
    }

    @Test fun particlesScaleWithFillAndArea() {
        assertEquals(LiquidEngine.FULL_PARTICLES, LiquidEngine.particlesFor(1f, 340f, 440f))
        assertEquals(LiquidEngine.FULL_PARTICLES / 2, LiquidEngine.particlesFor(0.5f, 340f, 440f))
        assertEquals(0, LiquidEngine.particlesFor(-1f, 340f, 440f))
        assertEquals(LiquidEngine.FULL_PARTICLES / 2, LiquidEngine.particlesFor(1f, 170f, 440f))
        assertEquals(LiquidSim.CAPACITY, LiquidEngine.particlesFor(1f, 2000f, 2000f))
    }

    @Test fun aFlatPhonePoursADiscInTheCentre() {
        val e = LiquidEngine(200f, 260f, Random(3)).apply { setGravity(0f, 0f, 1f); pour(100) }
        assertEquals(100f, e.meanX(), 8f)
        assertEquals(130f, e.meanY(), 8f)
    }

    @Test fun resizingKeepsTheJuiceInsideTheNewGlass() {
        val e = engine()
        e.run(60)
        e.resize(150f, 200f)
        e.run(120)
        assertEquals(150f, e.sim.width)
        assertTrue(e.allInsideTheGlass())
    }

    @Test fun theSameSeedGivesTheSameSlosh() {
        fun run() = engine(seed = 9).apply { shake(90) }.sim.x.copyOf(200)
        assertContentEquals(run(), run())
    }
}
