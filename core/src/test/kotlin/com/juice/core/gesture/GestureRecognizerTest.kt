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

    @Test
    fun circleScribbleIsNotRecognised() {
        val circle = (0..40).map {
            val a = it * 2 * PI / 40
            GesturePoint(100 + 80 * cos(a), 100 + 80 * sin(a))
        }
        assertEquals(GestureResult.NotRecognised, recogniser.recognise(circle))
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
    fun circleIsNotAStarWhenReadByStrokes() {
        val circle = (0..40).map {
            val a = it * 2 * PI / 40
            GesturePoint(100 + 80 * cos(a), 100 + 80 * sin(a))
        }
        assertEquals(GestureResult.NotRecognised, recogniser.recogniseStrokes(listOf(circle)))
    }
}
