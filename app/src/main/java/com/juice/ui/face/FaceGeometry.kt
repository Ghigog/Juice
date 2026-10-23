package com.juice.ui.face

import com.juice.core.character.Expression
import com.juice.core.character.Face
import com.juice.core.character.FeatureTransform
import com.juice.core.character.Shape

/** One shape to draw on a unit-square face: centre and size are fractions of the face, [rotation] is in degrees. */
data class ShapePlacement(
    val shape: Shape,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
)

/** Where a [Face] puts its two eyes and its mouth, in an [Expression], on a unit-square face. */
data class FaceGeometry(val leftEye: ShapePlacement, val rightEye: ShapePlacement, val mouth: ShapePlacement) {
    companion object {
        private const val EYE_INSET = 0.3f
        private const val EYE_Y = 0.38f
        private const val EYE_SIZE = 0.16f
        private const val MOUTH_Y = 0.7f
        private const val MOUTH_SIZE = 0.2f

        fun of(face: Face, expression: Expression): FaceGeometry {
            val eyes = expression.transform.eyes
            return FaceGeometry(
                leftEye = placement(face.eye, EYE_INSET, EYE_Y, EYE_SIZE, eyes, mirror = false),
                rightEye = placement(face.eye, 1f - EYE_INSET, EYE_Y, EYE_SIZE, eyes, mirror = true),
                mouth = placement(face.mouth, 0.5f, MOUTH_Y, MOUTH_SIZE, expression.transform.mouth, mirror = false),
            )
        }

        /** The right eye mirrors the left's rotation so slanted eyes lean towards or away from each other. */
        private fun placement(shape: Shape, x: Float, y: Float, size: Float, t: FeatureTransform, mirror: Boolean) = ShapePlacement(
            shape = shape,
            centerX = x + t.offsetX,
            centerY = y + t.offsetY,
            width = size * t.scale,
            height = size * t.scale * t.squash,
            rotation = if (mirror) -t.rotation else t.rotation,
        )
    }
}
