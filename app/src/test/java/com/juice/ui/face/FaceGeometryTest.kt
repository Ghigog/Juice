package com.juice.ui.face

import com.juice.core.character.Characters
import com.juice.core.character.Expression
import com.juice.core.character.Shape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FaceGeometryTest {
    private val jim = Characters.Jim.face

    @Test fun neutralJimHasTwoSquareEyesAndATriangleMouth() {
        val g = FaceGeometry.of(jim, Expression.NEUTRAL)
        assertEquals(Shape.SQUARE, g.leftEye.shape)
        assertEquals(Shape.SQUARE, g.rightEye.shape)
        assertEquals(Shape.TRIANGLE, g.mouth.shape)
    }

    @Test fun bothEyesAreTheSameSizeAndMirrorEachOther() {
        val g = FaceGeometry.of(jim, Expression.ANGRY)
        assertEquals(g.leftEye.width, g.rightEye.width)
        assertEquals(g.leftEye.height, g.rightEye.height)
        assertEquals(g.leftEye.rotation, -g.rightEye.rotation)
        assertEquals(1f, g.leftEye.centerX + g.rightEye.centerX)
    }

    @Test fun everyExpressionDrawsDifferentGeometry() {
        val all = Expression.entries.map { FaceGeometry.of(jim, it) }
        assertEquals(Expression.entries.size, all.toSet().size)
        assertNotEquals(all[0], all[1])
    }
}
