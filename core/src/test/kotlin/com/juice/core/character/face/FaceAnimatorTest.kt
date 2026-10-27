package com.juice.core.character.face

import com.juice.core.character.Characters
import com.juice.core.character.Expression
import com.juice.core.character.GeneratedPersona
import com.juice.core.character.toCharacter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FaceAnimatorTest {
    private val jim = Characters.Jim

    private fun animator(calm: Boolean = false, motion: FaceMotion = FaceMotion.Jim) =
        FaceAnimator(jim.face, motion, Expression.NEUTRAL, calm, Random(7))

    private fun FaceAnimator.run(seconds: Float) {
        var t = 0f
        while (t < seconds) { step(1f / 60f); t += 1f / 60f }
    }

    private fun stillPolys(e: Expression) = FaceAnimator.still(jim.face, e).polygons

    private fun close(a: List<FloatArray>, b: List<FloatArray>, tol: Float): Boolean =
        a.size == b.size && a.indices.all { i -> a[i].size == b[i].size && a[i].indices.all { j -> kotlin.math.abs(a[i][j] - b[i][j]) < tol } }

    @Test fun everyExpressionLooksDifferent() {
        val all = Expression.entries.map { e -> stillPolys(e).joinToString("|") { p -> p.joinToString(",") { "%.2f".format(it) } } }
        assertEquals(Expression.entries.size, all.toSet().size)
    }

    @Test fun aCalmFaceSettlesOnTheNewPoseWithoutWobbling() {
        val face = animator(calm = true)
        face.set(Expression.STERN)
        face.run(4f)
        val frame = face.frame()
        assertEquals(0f, frame.rotation)
        assertEquals(1f, frame.scaleX)
        assertTrue(frame.bubbles.isEmpty())
        // Eyes may be mid-blink or glancing; the mouth only follows the pose.
        assertTrue(close(listOf(frame.polygons[2]), listOf(stillPolys(Expression.STERN)[2]), 0.05f))
    }

    @Test fun changingExpressionPopsAndBurstsFroth() {
        val face = animator()
        face.set(Expression.EXCITED)
        face.step(1f / 60f)
        val frame = face.frame()
        assertTrue(frame.bubbles.isNotEmpty())
        assertNotEquals(1f, frame.scaleY)
        face.run(1f)
        assertTrue(face.frame().bubbles.isEmpty(), "froth is gone after a moment")
    }

    @Test fun theSpringsOvershootBeforeTheySettle() {
        val face = animator()
        face.set(Expression.SURPRISED)
        val target = FacePoses.of(Expression.SURPRISED).mouth.scaleY
        var peak = 0f
        repeat(60) { face.step(1f / 60f); peak = maxOf(peak, mouthHeight(face)) }
        val settledHeight = run { face.run(5f); mouthHeight(face) }
        assertTrue(peak > settledHeight * 1.05f, "overshoots: peak $peak settled $settledHeight target $target")
    }

    private fun mouthHeight(face: FaceAnimator): Float {
        val m = face.frame().polygons[2]
        val ys = m.filterIndexed { i, _ -> i % 2 == 1 }
        return ys.max() - ys.min()
    }

    @Test fun lettersOpenTheMouthAndItClosesAfter() {
        val face = animator(calm = true)
        face.run(1f)
        val shut = mouthHeight(face)
        repeat(6) { face.letter('a'); face.step(1f / 60f) }
        val open = mouthHeight(face)
        face.run(1f)
        assertTrue(open > shut * 1.3f, "open $open shut $shut")
        assertEquals(shut, mouthHeight(face), 0.1f)
    }

    @Test fun aSurprisedFaceNeverBlinks() {
        val face = animator(calm = true)
        face.set(Expression.SURPRISED)
        face.run(1f)
        val open = face.frame().polygons[0].filterIndexed { i, _ -> i % 2 == 1 }.let { it.max() - it.min() }
        repeat(60 * 12) {
            face.step(1f / 60f)
            val h = face.frame().polygons[0].filterIndexed { i, _ -> i % 2 == 1 }.let { it.max() - it.min() }
            assertTrue(h > open * 0.8f)
        }
    }

    @Test fun aNeutralFaceBlinksWithinAFewSeconds() {
        val face = animator(calm = true)
        val open = 18f
        var blinked = false
        repeat(60 * 8) {
            face.step(1f / 60f)
            val h = face.frame().polygons[0].filterIndexed { i, _ -> i % 2 == 1 }.let { it.max() - it.min() }
            if (h < open * 0.5f) blinked = true
        }
        assertTrue(blinked)
    }

    @Test fun aLongStallDoesNotBlowUpTheSprings() {
        val face = animator()
        face.set(Expression.EXCITED)
        face.step(10f)
        assertTrue(face.frame().polygons.all { p -> p.all { it.isFinite() && kotlin.math.abs(it) < 1000f } })
    }

    @Test fun builtInsHaveTheirOwnFeelAndCreatedOnesFollowTheirVoice() {
        assertEquals(FaceMotion.Jim, FaceMotion.of(Characters.Jim))
        assertEquals(FaceMotion.Nora, FaceMotion.of(Characters.Nutritionist))
        assertEquals(FaceMotion.Snooze, FaceMotion.of(Characters.SleepScientist))
        val created = GeneratedPersona.fallbackPersona("chess").toCharacter("c1", "chess")
        val low = FaceMotion.of(created.copy(mutterPitch = 0.5f))
        val high = FaceMotion.of(created.copy(mutterPitch = 2.0f))
        assertTrue(high.stiffness > low.stiffness)
        assertTrue(high.tempo > low.tempo)
    }
}
