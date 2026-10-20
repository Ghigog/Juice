package com.juice.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SloshSpringTest {
    @Test
    fun settlesBackToZeroAfterAnImpulse() {
        val spring = SloshSpring()
        spring.step(1f / 60, impulse = 30f)
        repeat(1000) { spring.step(1f / 60) }
        assertEquals(0f, spring.position, 1e-3f)
    }

    @Test
    fun outputIsClampedToUnitRange() {
        val spring = SloshSpring()
        repeat(120) { assertTrue(spring.step(1f / 60, impulse = 1000f) in -1f..1f) }
        repeat(120) { assertTrue(spring.step(1f / 60, impulse = -1000f) in -1f..1f) }
    }
}
