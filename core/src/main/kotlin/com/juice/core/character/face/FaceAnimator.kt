package com.juice.core.character.face

import com.juice.core.character.Character
import com.juice.core.character.Characters
import com.juice.core.character.Expression
import com.juice.core.character.Face
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How a character's face feels: [stiffness] and [damping] drive the springs between poses (low damping
 * overshoots and jiggles), [tempo] speeds its idle bob.
 */
data class FaceMotion(val stiffness: Float, val damping: Float, val tempo: Float) {
    companion object {
        /** Heavy jelly: slow, big wobble. */
        val Jim = FaceMotion(150f, 8f, 0.9f)
        /** Snappy and bouncy. */
        val Nora = FaceMotion(320f, 11f, 1.35f)
        /** Sluggish, heavily damped. */
        val Snooze = FaceMotion(80f, 10f, 0.6f)

        /** A built-in's own feel; any other character's follows its voice, higher pitch being snappier. */
        fun of(character: Character): FaceMotion = when (character.id) {
            Characters.Jim.id -> Jim
            Characters.Nutritionist.id -> Nora
            Characters.SleepScientist.id -> Snooze
            else -> {
                val t = ((character.mutterPitch - 0.5f) / 1.5f).coerceIn(0f, 1f)
                FaceMotion(80f + 260f * t, 8f + 4f * t, 0.6f + 0.8f * t)
            }
        }
    }
}

/** One froth bubble drawn this frame, in face units. */
data class FaceBubble(val x: Float, val y: Float, val radius: Float)

/**
 * One frame of a face: the three feature [polygons] (x,y pairs in face units, an empty one is cut away),
 * then the whole face moved by [offsetX], [offsetY], tilted [rotation] degrees and scaled by [scaleX], [scaleY]
 * about ([LidCutter.PIVOT_X], [LidCutter.PIVOT_Y]); [bubbles] burst out when the expression changes.
 */
class FaceFrame(
    val polygons: List<FloatArray>,
    val offsetX: Float,
    val offsetY: Float,
    val rotation: Float,
    val scaleX: Float,
    val scaleY: Float,
    val bubbles: List<FaceBubble>,
)

/**
 * A living face. Call [step] once per frame and draw [frame]. [set] springs it to a new expression with a
 * squash pop and a burst of froth; [letter] flaps the mouth while a line types out. It blinks, glances
 * around, breathes and bobs on its own. With [calm] (reduce motion) the springs settle without overshoot
 * and the face holds still: no bob, tilt, shiver, pop or froth.
 */
class FaceAnimator(
    private val face: Face,
    private val motion: FaceMotion,
    expression: Expression = Expression.NEUTRAL,
    var calm: Boolean = false,
    private val random: Random = Random.Default,
) {
    var expression: Expression = expression
        private set

    private var target = FacePoses.of(expression).toArray()
    private val current = target.copyOf()
    private val velocity = FloatArray(target.size)

    private var time = random.nextFloat() * 10f
    private var pop = 0f
    private var popVelocity = 0f
    private var blink = 0f
    private var blinkPhase = 0f
    private var nextBlink = 0.5f + random.nextFloat() * 2.5f
    private var gazeX = 0f
    private var gazeY = 0f
    private var gazeTargetX = 0f
    private var gazeTargetY = 0f
    private var nextGlance = 0.6f
    private var mouthOpen = 0f
    private var mouthTarget = 0f
    private val froth = mutableListOf<Froth>()

    private class Froth(val angle: Float, val distance: Float, val radius: Float, var life: Float = 0f)

    /** Springs to [expression]; the same one again just gives a small pop. */
    fun set(expression: Expression) {
        if (expression == this.expression) {
            if (!calm) popVelocity += 1.2f
            return
        }
        this.expression = expression
        target = FacePoses.of(expression).toArray()
        if (calm) return
        popVelocity += 2.4f
        repeat(FROTH_PER_POP) {
            froth += Froth(
                angle = random.nextFloat() * 2f * PI.toFloat(),
                distance = 30f + random.nextFloat() * 30f,
                radius = 2f + random.nextFloat() * 3.5f,
            )
        }
    }

    /** Looks towards ([x], [y]) face units from the middle for a moment, e.g. down at the text box. */
    fun glance(x: Float, y: Float, seconds: Float = 1.4f) {
        gazeTargetX = x
        gazeTargetY = y
        nextGlance = seconds
    }

    /** Opens the mouth for one typed letter: wide on a vowel, a little on a consonant, shut on a space or mark. */
    fun letter(char: Char) {
        val c = char.lowercaseChar()
        mouthTarget = when {
            c in "aeiouy" -> 1f
            c.isLetterOrDigit() -> 0.45f
            else -> 0.02f
        }
    }

    /** Advances by [dt] seconds (clamped to a 30 fps step so a stall never explodes the springs). */
    fun step(dt: Float) {
        val t = dt.coerceIn(0f, MAX_STEP)
        val k = motion.stiffness
        val c = if (calm) 2f * sqrt(k) else motion.damping
        for (i in current.indices) {
            val a = k * (target[i] - current[i]) - c * velocity[i]
            velocity[i] += a * t
            current[i] += velocity[i] * t
        }
        val popAccel = -POP_STIFFNESS * pop - POP_DAMPING * popVelocity
        popVelocity += popAccel * t
        pop += popVelocity * t

        val idle = FacePoses.of(expression).idle
        nextBlink -= t
        if (nextBlink <= 0f && blinkPhase == 0f) {
            if (idle.blinks) blinkPhase = 0.0001f
            nextBlink = if (random.nextFloat() < DOUBLE_BLINK_CHANCE) 0.22f else 2f + random.nextFloat() * 3.5f
        }
        if (blinkPhase > 0f) {
            blinkPhase += t / BLINK_SECONDS
            blink = if (blinkPhase < 0.5f) blinkPhase * 2f else (2f - blinkPhase * 2f).coerceAtLeast(0f)
            if (blinkPhase >= 1f) { blinkPhase = 0f; blink = 0f }
        }

        nextGlance -= t
        if (nextGlance <= 0f) {
            gazeTargetX = if (idle.glances) (random.nextFloat() - 0.5f) * 5f else 0f
            gazeTargetY = if (idle.glances) (random.nextFloat() - 0.5f) * 3f else 0f
            nextGlance = 1.1f + random.nextFloat() * 2.4f
        }
        val g = min(1f, t * 16f)
        gazeX += (gazeTargetX - gazeX) * g
        gazeY += (gazeTargetY - gazeY) * g

        mouthOpen += (mouthTarget - mouthOpen) * min(1f, t * 30f)
        mouthTarget = (mouthTarget - t * 5f).coerceAtLeast(0f)

        val bubbles = froth.iterator()
        while (bubbles.hasNext()) {
            val b = bubbles.next()
            b.life += t / FROTH_SECONDS
            if (b.life >= 1f) bubbles.remove()
        }
        time += t
    }

    fun frame(): FaceFrame {
        val idle = FacePoses.of(expression).idle
        val s = motion.tempo
        val wave = sin(time * idle.speed * 0.5f * s)
        val bob = when {
            calm -> 0f
            idle.hop -> -abs(wave) * idle.bob
            else -> wave * idle.bob
        }
        val tilt = if (calm) 0f else sin(time * idle.speed * 0.25f * s + 1f) * idle.tilt + idle.lean
        val jx = if (calm || idle.jitter == 0f) 0f else (random.nextFloat() - 0.5f) * idle.jitter * 2f
        val jy = if (calm || idle.jitter == 0f) 0f else (random.nextFloat() - 0.5f) * idle.jitter * 2f
        val p = if (calm) 0f else pop
        val breathe = if (calm) 1f else 1f + 0.04f * sin(time * 2.1f)

        val pose = current.copyOf()
        for (eye in 0..1) {
            val at = eye * FeaturePose.SIZE
            pose[at + 1] *= (1f - 0.92f * blink) * breathe
            pose[at + 3] += gazeX
            pose[at + 4] += gazeY
        }
        val m = 2 * FeaturePose.SIZE
        pose[m] *= 1f - mouthOpen * 0.15f
        pose[m + 1] *= 1f + mouthOpen * 1.1f

        val polygons = listOf(
            LidCutter.polygon(face.eye, LidCutter.LEFT_EYE, pose, 0),
            LidCutter.polygon(face.eye, LidCutter.RIGHT_EYE, pose, FeaturePose.SIZE),
            LidCutter.polygon(face.mouth, LidCutter.MOUTH, pose, m),
        )
        val bubbles = if (calm) emptyList() else froth.mapNotNull { b ->
            val eased = 1f - (1f - b.life).pow(3)
            val r = b.radius * (1f - b.life)
            if (r <= 0.2f) null
            else FaceBubble(LidCutter.PIVOT_X + cos(b.angle) * b.distance * eased, LidCutter.PIVOT_Y + sin(b.angle) * b.distance * eased, r)
        }
        return FaceFrame(polygons, jx, bob + jy, tilt, 1f - p * 0.6f, 1f + p, bubbles)
    }

    companion object {
        private const val MAX_STEP = 1f / 30f
        private const val POP_STIFFNESS = 260f
        private const val POP_DAMPING = 9f
        private const val BLINK_SECONDS = 0.16f
        private const val DOUBLE_BLINK_CHANCE = 0.18f
        private const val FROTH_PER_POP = 10
        private const val FROTH_SECONDS = 0.55f

        /** A still picture of [face] holding [expression], for avatars and anywhere that does not animate. */
        fun still(face: Face, expression: Expression): FaceFrame {
            val pose = FacePoses.of(expression).toArray()
            val polygons = listOf(
                LidCutter.polygon(face.eye, LidCutter.LEFT_EYE, pose, 0),
                LidCutter.polygon(face.eye, LidCutter.RIGHT_EYE, pose, FeaturePose.SIZE),
                LidCutter.polygon(face.mouth, LidCutter.MOUTH, pose, 2 * FeaturePose.SIZE),
            )
            return FaceFrame(polygons, 0f, 0f, 0f, 1f, 1f, emptyList())
        }
    }
}
