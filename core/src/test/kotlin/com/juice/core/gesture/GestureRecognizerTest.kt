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
}
