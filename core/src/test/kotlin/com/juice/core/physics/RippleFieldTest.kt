package com.juice.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RippleFieldTest {
    private val dt = 1f / 60
    private fun RippleField.at(x: Int, y: Int) = heights[y * RippleField.WIDTH + x]
    private fun RippleField.maxAbs() = heights.maxOf { kotlin.math.abs(it) }

    @Test
    fun pointDisturbanceSpreadsAsARingThenFades() {
        val field = RippleField()
        field.disturb(12f / 23, 8f / 15, 0.5f)
        var reached = false
        repeat(20) { field.step(dt); if (kotlin.math.abs(field.at(12 + 4, 8)) > 1e-3f) reached = true }
        assertTrue(reached)
        repeat(600) { field.step(dt) }
        assertEquals(0f, field.maxAbs(), 1e-3f)
    }

    @Test
    fun wakeDisturbsCellsAlongTheSegment() {
        val field = RippleField()
        field.wake(0f, 0.5f, 1f, 0.5f, 0.2f)
        assertTrue(field.at(0, 8) > 0f && field.at(RippleField.WIDTH - 1, 8) > 0f)
    }

    @Test
    fun valuesStayBoundedWithHugeFrameTime() {
        val field = RippleField()
        field.disturb(0.5f, 0.5f, 100f)
        repeat(100) { field.step(1f) }
        assertTrue(field.heights.all { it.isFinite() && it in -1f..1f })
    }
}
