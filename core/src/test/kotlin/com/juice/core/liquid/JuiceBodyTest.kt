package com.juice.core.liquid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JuiceBodyTest {
    @Test fun anUprightBodyIsFlatAcrossTheBottom() {
        assertEquals(300f, JuiceBody.level(0f, 1f, 0.25f, 200f, 400f), 0.01f)
        assertEquals(400f, JuiceBody.level(0f, 1f, 0f, 200f, 400f), 0.01f)
        assertEquals(0f, JuiceBody.level(0f, 1f, 1f, 200f, 400f), 0.01f)
    }

    @Test fun aTiltedBodyKeepsItsVolumeOnTheLowSide() {
        val nx = 0.6f; val ny = 0.8f
        val level = JuiceBody.level(nx, ny, 0.4f, 200f, 400f)
        val poly = JuiceBody.polygon(nx, ny, level, 200f, 400f)
        assertEquals(0.4f * 200f * 400f, JuiceBody.area(poly), 1f)
        // Every corner is on the low (gravity) side of the surface.
        for (i in 0 until poly.size / 2) assertTrue(poly[2 * i] * nx + poly[2 * i + 1] * ny >= level - 1e-2f)
        assertTrue(poly.indices.step(2).maxOf { poly[it] } == 200f)
    }

    @Test fun noBodyAboveTheGlass() {
        assertEquals(0, JuiceBody.polygon(0f, 1f, 500f, 200f, 400f).size)
    }
}
