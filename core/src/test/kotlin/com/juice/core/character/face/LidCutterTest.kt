package com.juice.core.character.face

import com.juice.core.character.Shape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LidCutterTest {
    private val eye = LidCutter.LEFT_EYE

    private fun pose(vararg p: Float) = FeaturePose(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]).toArray()
    private fun plain() = FeaturePose().toArray()

    private fun ys(poly: FloatArray) = poly.filterIndexed { i, _ -> i % 2 == 1 }
    private fun xs(poly: FloatArray) = poly.filterIndexed { i, _ -> i % 2 == 0 }

    @Test fun anUncutSquareIsItsFourCornersAroundTheSlot() {
        val poly = LidCutter.polygon(Shape.SQUARE, eye, plain())
        assertEquals(8, poly.size)
        assertEquals(eye.centerX - 9f, xs(poly).min(), 0.001f)
        assertEquals(eye.centerX + 9f, xs(poly).max(), 0.001f)
        assertEquals(eye.centerY - 9f, ys(poly).min(), 0.001f)
        assertEquals(eye.centerY + 9f, ys(poly).max(), 0.001f)
    }

    @Test fun aTopLidRemovesThatShareOfTheHeightFromTheTop() {
        val poly = LidCutter.polygon(Shape.CIRCLE, eye, FeaturePose(lidTop = 0.5f).toArray())
        assertEquals(eye.centerY, ys(poly).min(), 0.01f)
        assertEquals(eye.centerY + 9f, ys(poly).max(), 0.05f)
    }

    @Test fun aBottomLidRemovesThatShareFromTheBottom() {
        val poly = LidCutter.polygon(Shape.TRIANGLE, eye, FeaturePose(lidBottom = 0.5f).toArray())
        assertEquals(eye.centerY, ys(poly).max(), 0.01f)
    }

    @Test fun aPositiveLidAngleDropsTheInnerEndOnBothEyes() {
        val sloped = FeaturePose(lidTop = 0.3f, lidAngle = 25f).toArray()
        val left = LidCutter.polygon(Shape.SQUARE, LidCutter.LEFT_EYE, sloped)
        val right = LidCutter.polygon(Shape.SQUARE, LidCutter.RIGHT_EYE, sloped)
        // The left eye's inner side is to its right, the right eye's to its left: the top edge is lower there.
        fun topAt(poly: FloatArray, innerRight: Boolean): Pair<Float, Float> {
            val pts = poly.toList().chunked(2)
            val inner = if (innerRight) pts.maxBy { it[0] } else pts.minBy { it[0] }
            val outer = if (innerRight) pts.minBy { it[0] } else pts.maxBy { it[0] }
            val innerTop = pts.filter { abs(it[0] - inner[0]) < 0.01f }.minOf { it[1] }
            val outerTop = pts.filter { abs(it[0] - outer[0]) < 0.01f }.minOf { it[1] }
            return innerTop to outerTop
        }
        val (li, lo) = topAt(left, innerRight = true)
        val (ri, ro) = topAt(right, innerRight = false)
        assertTrue(li > lo, "left inner end is lower")
        assertTrue(ri > ro, "right inner end is lower")
        assertEquals(li - lo, ri - ro, 0.01f)
    }

    @Test fun lidsThatMeetCutEverythingAway() {
        val poly = LidCutter.polygon(Shape.SQUARE, eye, FeaturePose(lidTop = 0.6f, lidBottom = 0.6f).toArray())
        assertEquals(0, poly.size)
    }

    @Test fun aNegativeHeightFlipsATriangleIntoASmile() {
        val up = LidCutter.polygon(Shape.TRIANGLE, LidCutter.MOUTH, plain())
        val down = LidCutter.polygon(Shape.TRIANGLE, LidCutter.MOUTH, FeaturePose(scaleY = -1f).toArray())
        // The apex is the lone point: at the top when upright, at the bottom when flipped.
        assertEquals(ys(up).min(), ys(up)[0], 0.001f)
        assertEquals(ys(down).max(), ys(down)[0], 0.001f)
    }

    @Test fun everyShapeTakesEveryPose() {
        Shape.entries.forEach { shape ->
            com.juice.core.character.Expression.entries.forEach { e ->
                val pose = FacePoses.of(e).toArray()
                LidCutter.SLOTS.forEachIndexed { i, slot ->
                    val poly = LidCutter.polygon(shape, slot, pose, i * FeaturePose.SIZE)
                    assertTrue(poly.size >= 6, "$shape $e slot $i draws something")
                    assertTrue(poly.all { it.isFinite() })
                }
            }
        }
    }
}
