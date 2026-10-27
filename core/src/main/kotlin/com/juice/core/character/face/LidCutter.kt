package com.juice.core.character.face

import com.juice.core.character.Shape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** Where a feature sits on the 100 x 100 face: its centre, its base size, and +1 or -1 to mirror slopes and tilts. */
data class FeatureSlot(val centerX: Float, val centerY: Float, val size: Float, val mirror: Float)

/**
 * The lid cutter: draws a feature as one polygon by scaling its base shape, slicing it with the pose's lids,
 * then turning and placing it. Every shape takes every pose; nothing is drawn by hand.
 */
object LidCutter {
    val LEFT_EYE = FeatureSlot(32f, 42f, 18f, 1f)
    val RIGHT_EYE = FeatureSlot(68f, 42f, 18f, -1f)
    val MOUTH = FeatureSlot(50f, 72f, 20f, 1f)
    val SLOTS = listOf(LEFT_EYE, RIGHT_EYE, MOUTH)

    /** The face's middle: what it tilts and squashes about. */
    const val PIVOT_X = 50f
    const val PIVOT_Y = 56f

    private const val CIRCLE_POINTS = 28

    /** Unit outlines centred on the origin, x and y in -0.5..0.5, as x,y pairs. */
    private val base: Map<Shape, FloatArray> = mapOf(
        Shape.SQUARE to floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f),
        Shape.TRIANGLE to floatArrayOf(0f, -0.5f, 0.52f, 0.45f, -0.52f, 0.45f),
        Shape.CIRCLE to FloatArray(CIRCLE_POINTS * 2) { i ->
            val a = (i / 2) * 2.0 * PI / CIRCLE_POINTS
            (if (i % 2 == 0) cos(a) else sin(a)).toFloat() * 0.5f
        },
    )

    /**
     * The polygon for [shape] in [slot], posed by the 8 values of [pose] read from [at]
     * (see [FeaturePose]); x,y pairs in face units. Empty when the lids cut everything away.
     */
    fun polygon(shape: Shape, slot: FeatureSlot, pose: FloatArray, at: Int = 0): FloatArray {
        val sx = pose[at]; val sy = pose[at + 1]; val rot = pose[at + 2]
        val dx = pose[at + 3]; val dy = pose[at + 4]
        val lidTop = pose[at + 5]; val lidAngle = pose[at + 6]; val lidBottom = pose[at + 7]
        val w = slot.size * sx
        val h = slot.size * sy
        val height = max(abs(h), 0.001f)
        val outline = base.getValue(shape)
        var pts = FloatArray(outline.size) { i -> outline[i] * if (i % 2 == 0) w else h }
        if (lidTop > 0.002f) {
            // Keep what lies below the lid line y = top + lidTop * height + slope * x.
            val slope = tan(lidAngle * PI / 180.0).toFloat() * slot.mirror
            pts = clip(pts, -slope, 1f, -(-height / 2 + lidTop * height))
        }
        if (lidBottom > 0.002f) pts = clip(pts, 0f, -1f, height / 2 - lidBottom * height)
        return place(pts, slot.centerX + dx, slot.centerY + dy, rot * slot.mirror)
    }

    /** Keeps the part of [pts] where a*x + b*y + c >= 0 (one Sutherland-Hodgman pass). */
    internal fun clip(pts: FloatArray, a: Float, b: Float, c: Float): FloatArray {
        val n = pts.size / 2
        if (n == 0) return pts
        val out = ArrayList<Float>(pts.size + 4)
        for (i in 0 until n) {
            val px = pts[i * 2]; val py = pts[i * 2 + 1]
            val j = (i + 1) % n
            val qx = pts[j * 2]; val qy = pts[j * 2 + 1]
            val fp = a * px + b * py + c
            val fq = a * qx + b * qy + c
            if (fp >= 0f) { out += px; out += py }
            if ((fp >= 0f) != (fq >= 0f)) {
                val t = fp / (fp - fq)
                out += px + (qx - px) * t
                out += py + (qy - py) * t
            }
        }
        return out.toFloatArray()
    }

    private fun place(pts: FloatArray, cx: Float, cy: Float, degrees: Float): FloatArray {
        val a = degrees * PI / 180.0
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        return FloatArray(pts.size) { i ->
            val x = pts[i - i % 2]; val y = pts[i - i % 2 + 1]
            if (i % 2 == 0) cx + x * c - y * s else cy + x * s + y * c
        }
    }
}
