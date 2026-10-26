package com.juice.core.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class GesturePoint(val x: Double, val y: Double)

sealed interface GestureResult {
    /** A drawn ✓: completes the item. */
    data class Checkmark(val confidence: Double) : GestureResult

    /** A drawn ✗: dismisses the item. */
    data class Dismiss(val confidence: Double) : GestureResult

    /** A drawn ★, as five spokes or one continuous line: stars the item. */
    data class Star(val confidence: Double) : GestureResult

    data object NotRecognised : GestureResult
}

/**
 * $1 recogniser (Wobbrock et al.) without rotation normalisation: resample, scale to a
 * unit square, translate to the centroid, then compare with the templates by mean point distance.
 * Screen coordinates, y grows downward. Multi-stroke input is passed as one concatenated path, except for the five-spoke star, which
 * [recogniseStrokes] checks stroke by stroke because its stroke order is free.
 */
class GestureRecognizer(private val threshold: Double = DEFAULT_THRESHOLD) {

    private enum class Kind { CHECKMARK, DISMISS, STAR }

    private class Template(val kind: Kind, points: List<GesturePoint>) {
        val normalised = normalise(points)
    }

    private val templates = listOf(
        Template(Kind.CHECKMARK, listOf(p(0.0, 0.6), p(0.3, 1.0), p(1.0, 0.0))),
        // X as one path: both diagonals, in either stroke order and direction.
        Template(Kind.DISMISS, listOf(p(0.0, 0.0), p(1.0, 1.0), p(1.0, 0.0), p(0.0, 1.0))),
        Template(Kind.DISMISS, listOf(p(1.0, 0.0), p(0.0, 1.0), p(0.0, 0.0), p(1.0, 1.0))),
        Template(Kind.DISMISS, listOf(p(0.0, 0.0), p(1.0, 1.0), p(0.0, 1.0), p(1.0, 0.0))),
        Template(Kind.DISMISS, listOf(p(1.0, 0.0), p(0.0, 1.0), p(1.0, 1.0), p(0.0, 0.0))),
    ) + starTemplates()

    /** Reads the strokes of one drawing: five spokes make a star, anything else is matched as one joined path. */
    fun recogniseStrokes(strokes: List<List<GesturePoint>>): GestureResult =
        spokeStar(strokes) ?: recognise(strokes.flatten())

    fun recognise(stroke: List<GesturePoint>): GestureResult {
        if (stroke.size < 2 || pathLength(stroke) == 0.0) return GestureResult.NotRecognised
        val candidate = normalise(stroke)
        val best = templates
            .map { it to score(candidate, it.normalised) }
            .maxByOrNull { it.second } ?: return GestureResult.NotRecognised
        if (best.second < threshold) return GestureResult.NotRecognised
        return when (best.first.kind) {
            Kind.CHECKMARK -> GestureResult.Checkmark(best.second)
            Kind.DISMISS -> GestureResult.Dismiss(best.second)
            Kind.STAR -> GestureResult.Star(best.second)
        }
    }

    private fun spokeStar(strokes: List<List<GesturePoint>>): GestureResult.Star? {
        if (strokes.size != SPOKES) return null
        if (strokes.any { it.size < 2 || !isStraight(it) }) return null
        val points = strokes.flatten()
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        if (width <= 0 || height <= 0 || height / width !in SPOKE_ASPECT) return null
        val taken = BooleanArray(SPOKES)
        for (stroke in strokes) {
            val angle = lineAngle(stroke.first(), stroke.last())
            val edge = (0 until SPOKES).minByOrNull { angleGap(angle, it * 36.0) }!!
            if (taken[edge] || angleGap(angle, edge * 36.0) > SPOKE_TOLERANCE) return null
            taken[edge] = true
        }
        return GestureResult.Star(1.0)
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.8
        private const val RESAMPLE_COUNT = 64
        private const val SQUARE_SIZE = 250.0
        private val HALF_DIAGONAL = 0.5 * hypot(SQUARE_SIZE, SQUARE_SIZE)

        private const val SPOKES = 5
        private const val SPOKE_TOLERANCE = 15.0
        private const val MIN_STRAIGHTNESS = 0.85
        private val SPOKE_ASPECT = 0.75..1.33

        private fun p(x: Double, y: Double) = GesturePoint(x, y)

        /** The unicursal pentagram as one path (every second vertex, closed), from any vertex and in either direction. */
        private fun starTemplates(): List<Template> {
            val vertices = (0 until SPOKES).map { p(sin(it * 2 * PI / SPOKES), -cos(it * 2 * PI / SPOKES)) }
            return (0 until SPOKES).flatMap { start ->
                listOf(2, 3).map { step ->
                    Template(Kind.STAR, (0..SPOKES).map { vertices[(start + it * step) % SPOKES] })
                }
            }
        }

        private fun isStraight(stroke: List<GesturePoint>): Boolean {
            val length = pathLength(stroke)
            return length > 0 && hypot(stroke.last().x - stroke.first().x, stroke.last().y - stroke.first().y) / length >= MIN_STRAIGHTNESS
        }

        /** Direction of the line from [a] to [b] in degrees, mod 180, in 0 until 180. */
        private fun lineAngle(a: GesturePoint, b: GesturePoint): Double =
            (Math.toDegrees(atan2(b.y - a.y, b.x - a.x)) + 360.0) % 180.0

        private fun angleGap(a: Double, b: Double): Double = abs(a - b).let { min(it, 180.0 - it) }

        private fun score(a: List<GesturePoint>, b: List<GesturePoint>): Double {
            val distance = a.indices.sumOf { hypot(a[it].x - b[it].x, a[it].y - b[it].y) } / a.size
            return 1.0 - distance / HALF_DIAGONAL
        }

        private fun pathLength(points: List<GesturePoint>): Double =
            points.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }

        private fun normalise(points: List<GesturePoint>): List<GesturePoint> {
            val resampled = resample(points)
            val minX = resampled.minOf { it.x }
            val maxX = resampled.maxOf { it.x }
            val minY = resampled.minOf { it.y }
            val maxY = resampled.maxOf { it.y }
            // Floor the extent so a straight line is not blown up to a full square.
            val extent = max(max(maxX - minX, maxY - minY), 1e-9)
            val width = max(maxX - minX, extent * MIN_ASPECT)
            val height = max(maxY - minY, extent * MIN_ASPECT)
            val scaled = resampled.map {
                GesturePoint((it.x - minX) / width * SQUARE_SIZE, (it.y - minY) / height * SQUARE_SIZE)
            }
            val cx = scaled.sumOf { it.x } / scaled.size
            val cy = scaled.sumOf { it.y } / scaled.size
            return scaled.map { GesturePoint(it.x - cx, it.y - cy) }
        }

        private const val MIN_ASPECT = 0.2

        private fun resample(points: List<GesturePoint>): List<GesturePoint> {
            val interval = pathLength(points) / (RESAMPLE_COUNT - 1)
            val work = points.toMutableList()
            val out = mutableListOf(work[0])
            var accumulated = 0.0
            var i = 1
            while (i < work.size) {
                val prev = work[i - 1]
                val cur = work[i]
                val d = hypot(cur.x - prev.x, cur.y - prev.y)
                if (d > 0 && accumulated + d >= interval) {
                    val t = (interval - accumulated) / d
                    val q = GesturePoint(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                    out.add(q)
                    work.add(i, q)
                    accumulated = 0.0
                } else {
                    accumulated += d
                }
                i++
            }
            while (out.size < RESAMPLE_COUNT) out.add(work.last())
            return out.take(RESAMPLE_COUNT)
        }
    }
}
