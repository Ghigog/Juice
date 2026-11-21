package com.juice.core.gesture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GestureRecognizerTest {
    private val recogniser = GestureRecognizer()

    private fun path(vararg xy: Double) = xy.toList().chunked(2).map { GesturePoint(it[0], it[1]) }

    // Recorded-style samples: sparse, uneven touch points in screen pixels.
    private val checkmarks = listOf(
        path(100.0, 300.0, 130.0, 340.0, 160.0, 380.0, 200.0, 330.0, 260.0, 230.0, 330.0, 130.0),
        path(10.0, 52.0, 22.0, 70.0, 35.0, 88.0, 50.0, 70.0, 80.0, 30.0, 120.0, 5.0),
        path(500.0, 800.0, 540.0, 880.0, 560.0, 905.0, 640.0, 780.0, 760.0, 640.0),
    )

    private val crosses = listOf(
        path(100.0, 100.0, 150.0, 150.0, 200.0, 200.0, 200.0, 100.0, 150.0, 150.0, 100.0, 200.0),
        path(40.0, 45.0, 90.0, 100.0, 140.0, 155.0, 142.0, 48.0, 92.0, 102.0, 38.0, 150.0),
        path(300.0, 500.0, 360.0, 560.0, 420.0, 620.0, 300.0, 620.0, 360.0, 560.0, 420.0, 500.0),
    )

    @Test
    fun variedCheckmarksAreRecognisedAsCheckmark() {
        checkmarks.forEach { assertIs<GestureResult.Checkmark>(recogniser.recognise(it)) }
    }

    @Test
    fun crossesAreRecognisedAsDismiss() {
        crosses.forEach { assertIs<GestureResult.Dismiss>(recogniser.recognise(it)) }
    }

    private fun circle(clockwise: Boolean, start: Double, cx: Double = 100.0, cy: Double = 100.0, radius: Double = 80.0) =
        (0..40).map {
            val a = start + (if (clockwise) 1 else -1) * it * 2 * PI / 40
            GesturePoint(cx + radius * cos(a), cy + radius * sin(a))
        }

    @Test
    fun circlesAreReadInTheirDrawnDirectionFromAnyStart() {
        listOf(0.0, 1.0, 2.5, 4.0, 5.5).forEach { start ->
            assertEquals(true, assertIs<GestureResult.Circle>(recogniser.recognise(circle(true, start))).clockwise)
            assertEquals(false, assertIs<GestureResult.Circle>(recogniser.recognise(circle(false, start))).clockwise)
        }
    }

    @Test
    fun circlesAreReadWhateverTheirSizeOrPlace() {
        assertIs<GestureResult.Circle>(recogniser.recognise(circle(true, 0.3, 500.0, 900.0, 20.0)))
    }

    @Test
    fun marksAreNeverTakenForCircles() {
        (checkmarks + crosses).forEach { assertEquals(false, recogniser.recognise(it) is GestureResult.Circle) }
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(listOf((0..5).map { vertex[(it * 2) % 5] })))
    }

    @Test
    fun zigzagScribbleIsNotRecognised() {
        val zigzag = path(0.0, 0.0, 100.0, 10.0, 0.0, 20.0, 100.0, 30.0, 0.0, 40.0, 100.0, 50.0, 0.0, 60.0)
        assertEquals(GestureResult.NotRecognised, recogniser.recognise(zigzag))
    }

    @Test
    fun degenerateStrokesAreNotRecognised() {
        assertEquals(GestureResult.NotRecognised, recogniser.recognise(emptyList()))
        assertEquals(GestureResult.NotRecognised, recogniser.recognise(path(5.0, 5.0, 5.0, 5.0)))
    }

    // Star vertices, y down: top, upper right, bottom right, bottom left, upper left.
    private val vertex = (0 until 5).map { GesturePoint(300 + 100 * sin(it * 2 * PI / 5), 300 - 100 * cos(it * 2 * PI / 5)) }

    private fun line(a: GesturePoint, b: GesturePoint, wobble: Double = 0.0) = (0..6).map {
        val t = it / 6.0
        val bump = if (it % 2 == 1) wobble else 0.0
        GesturePoint(a.x + (b.x - a.x) * t + bump, a.y + (b.y - a.y) * t - bump)
    }

    /** Bottom right, top, bottom left, far right, far left, back to bottom right. */
    private fun oneLineStar(wobble: Double = 0.0) =
        listOf(2, 0, 3, 1, 4, 2).zipWithNext().flatMap { (a, b) -> line(vertex[a], vertex[b], wobble) }

    private val spokes = listOf(2 to 0, 0 to 3, 3 to 1, 1 to 4, 4 to 2).map { (a, b) -> line(vertex[a], vertex[b]) }

    @Test
    fun oneLineStarIsRecognisedAsStar() {
        assertIs<GestureResult.Star>(recogniser.recognise(oneLineStar()))
        assertIs<GestureResult.Star>(recogniser.recognise(oneLineStar(wobble = 3.0)))
        assertIs<GestureResult.Star>(recogniser.recognise(oneLineStar().reversed()))
    }

    @Test
    fun oneLineStarInSeveralStrokesIsRecognisedAsStar() {
        val joined = listOf(2, 0, 3, 1, 4, 2).zipWithNext().map { (a, b) -> line(vertex[a], vertex[b]) }
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(joined.take(2).map { it } + joined.drop(2)))
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(listOf(joined.take(3).flatten(), joined.drop(3).flatten())))
    }

    @Test
    fun fiveSpokesInAnyOrderOrDirectionAreAStar() {
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(spokes))
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(spokes.reversed().map { it.reversed() }))
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(listOf(spokes[3], spokes[0], spokes[4].reversed(), spokes[2], spokes[1])))
        assertIs<GestureResult.Star>(recogniser.recogniseStrokes(spokes.map { line(it.first(), it.last(), wobble = 2.0) }))
    }

    @Test
    fun fourSpokesAreNotAStar() {
        assertEquals(GestureResult.NotRecognised, recogniser.recogniseStrokes(spokes.take(4)))
    }

    @Test
    fun fiveSpokesInTheWrongDirectionsAreNotAStar() {
        assertEquals(GestureResult.NotRecognised, recogniser.recogniseStrokes(List(5) { line(GesturePoint(0.0, 0.0), GesturePoint(100.0, 100.0)) }))
    }

    @Test
    fun crossesAndCheckmarksStayWhatTheyAreWhenReadByStrokes() {
        assertIs<GestureResult.Dismiss>(recogniser.recogniseStrokes(listOf(crosses[0].take(3), crosses[0].drop(3))))
        crosses.forEach { assertIs<GestureResult.Dismiss>(recogniser.recogniseStrokes(listOf(it))) }
        checkmarks.forEach { assertIs<GestureResult.Checkmark>(recogniser.recogniseStrokes(listOf(it))) }
    }

    @Test
    fun circleIsACircleNotAStarWhenReadByStrokes() {
        val circle = (0..40).map {
            val a = it * 2 * PI / 40
            GesturePoint(100 + 80 * cos(a), 100 + 80 * sin(a))
        }
        assertIs<GestureResult.Circle>(recogniser.recogniseStrokes(listOf(circle)))
    }

    private fun tick(scale: Double, x: Double = 0.0, y: Double = 0.0) =
        path(0.0, 0.0, 30.0, 40.0, 100.0, -50.0).map { GesturePoint(x + it.x * scale, y + it.y * scale) }

    private fun assertRead(expected: GestureResult, strokes: List<List<GesturePoint>>) =
        assertEquals(expected::class, recogniser.recogniseStrokes(strokes)::class)

    @Test
    fun tickIsATickWhateverItsSizeOrPlaceInOneStrokeOrTwo() {
        listOf(tick(1.0), tick(0.2, 300.0, 40.0), tick(3.0, 20.0, 200.0)).forEach {
            val result = assertIs<GestureResult.Checkmark>(recogniser.recognise(it))
            assertEquals(true, result.confidence in 0.0..1.0)
            assertIs<GestureResult.Checkmark>(recogniser.recogniseStrokes(listOf(it.take(2), it.drop(1))))
        }
    }

    @Test
    fun aLoneUpRightLineIsNotATick() {
        assertRead(GestureResult.NotRecognised, listOf(line(GesturePoint(0.0, 100.0), GesturePoint(100.0, 0.0))))
        assertRead(GestureResult.NotRecognised, listOf(line(GesturePoint(0.0, 30.0), GesturePoint(30.0, 0.0))))
    }

    @Test
    fun aDownRightPartAsLongAsTheUpRightOneOrLongerIsNotATick() {
        assertRead(GestureResult.NotRecognised, listOf(path(0.0, 0.0, 60.0, 80.0, 110.0, 20.0)))
        assertRead(GestureResult.NotRecognised, listOf(path(0.0, 0.0, 60.0, 80.0, 100.0, 30.0)))
        assertRead(GestureResult.NotRecognised, listOf(path(0.0, 0.0, 60.0, 80.0, 80.0, 55.0)))
    }

    @Test
    fun aNegligibleHookIsNotATick() {
        assertRead(GestureResult.NotRecognised, listOf(path(0.0, 0.0, 9.0, 12.0, 69.0, -63.0)))
    }

    private val backslash = line(GesturePoint(100.0, 100.0), GesturePoint(200.0, 220.0))
    private val slash = line(GesturePoint(200.0, 100.0), GesturePoint(100.0, 220.0))

    private fun scaled(points: List<GesturePoint>, k: Double, x: Double, y: Double) =
        points.map { GesturePoint(x + it.x * k, y + it.y * k) }

    @Test
    fun anyCrossingMirroredSlantsAreAnXInEveryOrderAndDirection() {
        val orders = listOf(
            backslash to slash, slash to backslash,
            backslash.reversed() to slash, backslash to slash.reversed(),
            slash.reversed() to backslash.reversed(), slash to backslash.reversed(),
        )
        orders.forEach { (first, second) ->
            val result = assertIs<GestureResult.Dismiss>(recogniser.recogniseStrokes(listOf(first, second)))
            assertEquals(true, result.confidence in 0.0..1.0)
            assertIs<GestureResult.Dismiss>(recogniser.recognise(first + second))
        }
    }

    @Test
    fun smallAndLargeXesReadTheSame() {
        listOf(0.1 to 400.0, 4.0 to 0.0).forEach { (k, offset) ->
            val strokes = listOf(backslash, slash).map { scaled(it, k, offset, offset) }
            assertIs<GestureResult.Dismiss>(recogniser.recogniseStrokes(strokes))
            assertIs<GestureResult.Dismiss>(recogniser.recognise(strokes.flatten()))
        }
    }

    @Test
    fun shapesThatAreNotAnXAreNotRecognised() {
        val plus = listOf(line(GesturePoint(50.0, 0.0), GesturePoint(50.0, 100.0)), line(GesturePoint(0.0, 50.0), GesturePoint(100.0, 50.0)))
        val parallel = listOf(backslash, scaled(backslash, 1.0, 40.0, 0.0))
        val steepnessDiffers = listOf(backslash, line(GesturePoint(200.0, 100.0), GesturePoint(100.0, 140.0)))
        val apart = listOf(line(GesturePoint(0.0, 0.0), GesturePoint(100.0, 120.0)), line(GesturePoint(300.0, 0.0), GesturePoint(200.0, 120.0)))
        listOf(plus, parallel, steepnessDiffers, apart).forEach {
            assertRead(GestureResult.NotRecognised, it)
            assertEquals(GestureResult.NotRecognised, recogniser.recognise(it.flatten()))
        }
    }

    @Test
    fun linesMeetingAtAnEndAreNotAnX() {
        val v = listOf(line(GesturePoint(100.0, 100.0), GesturePoint(160.0, 200.0)), line(GesturePoint(160.0, 200.0), GesturePoint(220.0, 100.0)))
        assertRead(GestureResult.NotRecognised, v)
    }
}
