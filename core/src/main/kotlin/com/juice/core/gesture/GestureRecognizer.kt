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

    /** A drawn circle: [clockwise] as seen on screen picks Oldest first, anticlockwise Newest first. */
    data class Circle(val clockwise: Boolean, val confidence: Double) : GestureResult

    data object NotRecognised : GestureResult
}

/**
 * Tick and X are read by shape: the path is split into near-straight segments at sharp turns, and only
 * lengths and angles are compared, so size and place do not matter. A tick is a down-right segment then a
 * longer up-right one; an X is two crossing lines of mirrored slant, drawn in any order and direction.
 * Stars and circles use a $1 recogniser (Wobbrock et al.) without rotation normalisation: resample, scale to a
 * unit square, translate to the centroid, then compare with the templates by mean point distance.
 * Screen coordinates, y grows downward. Multi-stroke input is passed as one concatenated path, except for the five-spoke star, which
 * [recogniseStrokes] checks stroke by stroke because its stroke order is free.
 */
class GestureRecognizer(private val threshold: Double = DEFAULT_THRESHOLD) {

    private enum class Kind { STAR, CIRCLE_CLOCKWISE, CIRCLE_ANTICLOCKWISE }

    private class Template(val kind: Kind, points: List<GesturePoint>) {
        val normalised = normalise(points)
    }

    private val templates = starTemplates() + circleTemplates()

    /** Reads the strokes of one drawing: five spokes make a star, anything else is matched as one joined path. */
    fun recogniseStrokes(strokes: List<List<GesturePoint>>): GestureResult =
        spokeStar(strokes) ?: recognise(strokes.flatten())

    fun recognise(stroke: List<GesturePoint>): GestureResult {
        if (stroke.size < 2 || pathLength(stroke) == 0.0) return GestureResult.NotRecognised
        val segments = segments(stroke)
        tick(segments)?.let { return GestureResult.Checkmark(it) }
        cross(segments)?.let { return GestureResult.Dismiss(it) }
        val candidate = normalise(stroke)
        val best = templates
            .map { it to score(candidate, it.normalised) }
            .maxByOrNull { it.second } ?: return GestureResult.NotRecognised
        if (best.second < threshold) return GestureResult.NotRecognised
        return when (best.first.kind) {
            Kind.STAR -> GestureResult.Star(best.second)
            Kind.CIRCLE_CLOCKWISE -> GestureResult.Circle(clockwise = true, best.second)
            Kind.CIRCLE_ANTICLOCKWISE -> GestureResult.Circle(clockwise = false, best.second)
        }
    }

    private class Segment(val start: GesturePoint, val end: GesturePoint, val points: List<GesturePoint>) {
        val length = pathLength(points)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val straightness = if (length > 0) hypot(dx, dy) / length else 0.0
        val angle = lineAngle(start, end)
    }

    /** Splits the path where it turns sharply, then folds slivers into a neighbour. */
    private fun segments(stroke: List<GesturePoint>): List<Segment> {
        val groups = mutableListOf<MutableList<GesturePoint>>()
        var current = mutableListOf(stroke.first())
        for (point in stroke.drop(1)) {
            val last = current.last()
            if (point == last) continue
            if (current.size >= 2) {
                val chord = current.first()
                val turn = angleBetween(last.x - chord.x, last.y - chord.y, point.x - last.x, point.y - last.y)
                if (turn > SHARP_TURN) {
                    groups.add(current)
                    current = mutableListOf(last)
                }
            }
            current.add(point)
        }
        groups.add(current)
        val total = pathLength(stroke)
        val merged = mutableListOf<MutableList<GesturePoint>>()
        for (group in groups) {
            if (merged.isNotEmpty() && pathLength(group) < total * SLIVER) merged.last().addAll(group.drop(1))
            else if (merged.isNotEmpty() && pathLength(merged.last()) < total * SLIVER) merged.last().addAll(group.drop(1))
            else merged.add(group)
        }
        return merged.filter { it.size >= 2 }.map { Segment(it.first(), it.last(), it) }
    }

    /** A down-right segment then a longer up-right one, or null. */
    private fun tick(segments: List<Segment>): Double? {
        if (segments.size != 2 || segments.any { it.straightness < MIN_STRAIGHTNESS }) return null
        val (down, up) = segments
        if (down.dx <= 0 || down.dy <= 0 || up.dx <= 0 || up.dy >= 0) return null
        if (down.angle !in SLANT || 180.0 - up.angle !in SLANT) return null
        if (up.length < down.length * TICK_RATIO || down.length < up.length * TICK_MIN_HOOK) return null
        return confidence(segments.sumOf { it.straightness } / 2)
    }

    /** Two long mirror-slanted lines that cross, alone or joined by one shorter connector, or null. */
    private fun cross(segments: List<Segment>): Double? {
        val (a, b) = when (segments.size) {
            2 -> segments[0] to segments[1]
            3 -> if (segments[1].length < minOf(segments[0].length, segments[2].length)) segments[0] to segments[2] else return null
            else -> return null
        }
        if (a.straightness < MIN_STRAIGHTNESS || b.straightness < MIN_STRAIGHTNESS) return null
        val (backslash, slash) = if (a.angle in SLANT) a to b else b to a
        if (backslash.angle !in SLANT || slash.angle !in (180.0 - SLANT.endInclusive)..(180.0 - SLANT.start)) return null
        val mirror = abs(backslash.angle - (180.0 - slash.angle))
        if (mirror > MIRROR_TOLERANCE) return null
        val denominator = backslash.dx * slash.dy - backslash.dy * slash.dx
        if (abs(denominator) < 1e-9) return null
        val gx = slash.start.x - backslash.start.x
        val gy = slash.start.y - backslash.start.y
        val t = (gx * slash.dy - gy * slash.dx) / denominator
        val u = (gx * backslash.dy - gy * backslash.dx) / denominator
        if (t !in CROSSING || u !in CROSSING) return null
        return confidence((1.0 - mirror / MIRROR_TOLERANCE + (a.straightness + b.straightness) / 2) / 2)
    }

    private fun confidence(value: Double) = value.coerceIn(0.0, 1.0)

    /** Unsigned angle in degrees between two direction vectors, 0..180. */
    private fun angleBetween(ax: Double, ay: Double, bx: Double, by: Double): Double =
        Math.toDegrees(abs(atan2(ax * by - ay * bx, ax * bx + ay * by)))

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
        private const val SHARP_TURN = 45.0
        private const val SLIVER = 0.1
        private const val TICK_RATIO = 1.2
        private const val TICK_MIN_HOOK = 0.2
        private const val MIRROR_TOLERANCE = 25.0
        private val SLANT = 15.0..75.0
        private val CROSSING = 0.15..0.85
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

        /** A closed circle from several start points, in both directions; y grows downward, so increasing angle is clockwise on screen. */
        private fun circleTemplates(): List<Template> =
            (0 until CIRCLE_STARTS).flatMap { start ->
                val offset = start * 2 * PI / CIRCLE_STARTS
                listOf(Kind.CIRCLE_CLOCKWISE to 1, Kind.CIRCLE_ANTICLOCKWISE to -1).map { (kind, sign) ->
                    Template(kind, (0..CIRCLE_STEPS).map {
                        val a = offset + sign * it * 2 * PI / CIRCLE_STEPS
                        p(cos(a), sin(a))
                    })
                }
            }

        private const val CIRCLE_STARTS = 16
        private const val CIRCLE_STEPS = 32

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
