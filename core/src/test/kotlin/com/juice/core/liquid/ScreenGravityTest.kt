package com.juice.core.liquid

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenGravityTest {
    private val g = ScreenGravity.EARTH

    private fun assertGravity(x: Float, y: Float, z: Float, actual: ScreenGravity) {
        assertEquals(x, actual.x, 1e-4f); assertEquals(y, actual.y, 1e-4f); assertEquals(z, actual.z, 1e-4f)
    }

    @Test fun uprightGravityPointsDownTheScreen() {
        assertGravity(0f, 1f, 0f, ScreenGravity.of(0f, g, 0f, 0))
        assertGravity(0f, -1f, 0f, ScreenGravity.of(0f, -g, 0f, 0))
    }

    @Test fun tiltingRightRunsGravityRight() {
        // Tilted right, the device's -x edge is low and the accelerometer reads -x.
        assertGravity(1f, 0f, 0f, ScreenGravity.of(-g, 0f, 0f, 0))
    }

    @Test fun lyingFlatIsAllIntoTheScreen() {
        assertGravity(0f, 0f, 1f, ScreenGravity.of(0f, 0f, g, 0))
        assertGravity(0f, 0f, 1f, ScreenGravity.of(0f, 0f, -2 * g, 0))
    }

    @Test fun gravityFollowsTheDisplayRotation() {
        // Landscape (rotated counter-clockwise): the device's +x edge is the top of the screen.
        assertGravity(0f, 1f, 0f, ScreenGravity.of(g, 0f, 0f, 1))
        assertGravity(0f, -1f, 0f, ScreenGravity.of(0f, g, 0f, 2))
        assertGravity(0f, 1f, 0f, ScreenGravity.of(-g, 0f, 0f, 3))
        assertGravity(-1f, 0f, 0f, ScreenGravity.of(0f, g, 0f, 3))
    }
}
