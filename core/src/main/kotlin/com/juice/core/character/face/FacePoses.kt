package com.juice.core.character.face

import com.juice.core.character.Expression

/**
 * How one feature (an eye or the mouth) sits in a pose. Sizes are factors of the feature's base size;
 * a negative [scaleY] flips the shape upside down (a triangle mouth becomes a smile). Offsets are in face
 * units (the face is 100 x 100, +y is down) and [rotation] is in degrees.
 *
 * The lids cut the shape: [lidTop] removes that share of its height from the top along a line sloped by
 * [lidAngle] degrees (positive drops the inner end, an angry brow; negative drops the outer end, a worried one),
 * and [lidBottom] removes that share from the bottom (a happy squint). The cuts work the same on any shape.
 */
data class FeaturePose(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val lidTop: Float = 0f,
    val lidAngle: Float = 0f,
    val lidBottom: Float = 0f,
) {
    fun toArray(): FloatArray = floatArrayOf(scaleX, scaleY, rotation, offsetX, offsetY, lidTop, lidAngle, lidBottom)

    companion object {
        const val SIZE = 8
    }
}

/**
 * How the whole face moves while it holds a pose: it bobs [bob] face units [speed] times faster than
 * a calm face, tilts up to [tilt] degrees, shivers by [jitter] units, and with [hop] bounces off the floor.
 * [blinks] is false for poses that keep their eyes wide; [glances] is false for poses that hold their gaze.
 */
data class IdleMotion(
    val bob: Float,
    val speed: Float,
    val tilt: Float,
    val jitter: Float = 0f,
    val hop: Boolean = false,
    val blinks: Boolean = true,
    val glances: Boolean = true,
    val lean: Float = 0f,
)

data class FacePose(val left: FeaturePose, val right: FeaturePose, val mouth: FeaturePose, val idle: IdleMotion) {
    /** The three features as one flat array: left eye, right eye, mouth, [FeaturePose.SIZE] values each. */
    fun toArray(): FloatArray = left.toArray() + right.toArray() + mouth.toArray()
}

/** The lid-cutter pose for every [Expression]; it fits any eye and mouth shape. */
object FacePoses {
    private fun eyes(p: FeaturePose) = p to p

    private fun pose(eyes: Pair<FeaturePose, FeaturePose>, mouth: FeaturePose, idle: IdleMotion) =
        FacePose(eyes.first, eyes.second, mouth, idle)

    private val poses: Map<Expression, FacePose> = mapOf(
        Expression.NEUTRAL to pose(
            eyes(FeaturePose()),
            FeaturePose(scaleX = 1.2f, scaleY = 0.75f),
            IdleMotion(bob = 1.6f, speed = 2.2f, tilt = 2f),
        ),
        Expression.HAPPY to pose(
            eyes(FeaturePose(scaleX = 1.08f, offsetY = -2f, lidBottom = 0.5f)),
            FeaturePose(scaleX = 1.5f, scaleY = -1.1f, offsetY = -1f, lidTop = 0.4f),
            IdleMotion(bob = 3f, speed = 4f, tilt = 4f),
        ),
        Expression.EXCITED to pose(
            eyes(FeaturePose(scaleX = 1.3f, scaleY = 1.3f, offsetY = -4f)),
            FeaturePose(scaleX = 1.7f, scaleY = -1.5f, offsetY = 1f, lidTop = 0.25f),
            IdleMotion(bob = 7f, speed = 9f, tilt = 5f, jitter = 0.3f, hop = true),
        ),
        Expression.WORRIED to pose(
            eyes(FeaturePose(scaleX = 0.95f, offsetY = 1f, lidTop = 0.38f, lidAngle = -24f)),
            FeaturePose(scaleX = 1.0f, scaleY = 0.4f, offsetY = 2f),
            IdleMotion(bob = 1f, speed = 3f, tilt = 1f, jitter = 0.9f),
        ),
        Expression.SAD to pose(
            eyes(FeaturePose(scaleY = 0.9f, offsetY = 5f, lidTop = 0.32f, lidAngle = -14f)),
            FeaturePose(scaleX = 0.9f, scaleY = 0.6f, offsetY = 6f, lidBottom = 0.45f),
            IdleMotion(bob = 1f, speed = 1.2f, tilt = 1.5f),
        ),
        Expression.STERN to pose(
            eyes(FeaturePose(scaleY = 0.95f, lidTop = 0.48f, lidAngle = 22f)),
            FeaturePose(scaleX = 1.5f, scaleY = 0.26f, offsetY = 1f),
            IdleMotion(bob = 0.4f, speed = 1.5f, tilt = 0.5f),
        ),
        Expression.SURPRISED to pose(
            eyes(FeaturePose(scaleX = 1.4f, scaleY = 1.4f, offsetY = -4f)),
            FeaturePose(scaleX = 0.8f, scaleY = 1.4f, offsetY = 3f),
            IdleMotion(bob = 2f, speed = 5f, tilt = 0f, jitter = 0.2f, blinks = false),
        ),
        Expression.THINKING to FacePose(
            FeaturePose(offsetX = 5f, offsetY = -4f, lidTop = 0.35f),
            FeaturePose(scaleX = 1.1f, scaleY = 1.1f, offsetX = 5f, offsetY = -6f),
            FeaturePose(scaleX = 0.7f, scaleY = 0.5f, rotation = -14f, offsetX = 7f),
            IdleMotion(bob = 1.2f, speed = 1.4f, tilt = 6f, glances = false),
        ),
        Expression.CHEEKY to FacePose(
            FeaturePose(scaleY = 0.14f, offsetY = 1f),
            FeaturePose(offsetY = -1f, lidTop = 0.2f, lidAngle = 10f),
            FeaturePose(scaleX = 1.2f, scaleY = -0.8f, rotation = 16f, offsetX = 6f, lidTop = 0.2f),
            IdleMotion(bob = 2.5f, speed = 3.5f, tilt = 7f, lean = 6f),
        ),
    )

    fun of(expression: Expression): FacePose = poses.getValue(expression)
}
