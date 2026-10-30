package com.juice.ui.liquid

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class QuadBatchTest {
    @Test fun eachSpriteIsASquareAroundItsCentre() {
        val b = QuadBatch(2, 64f)
        b.add(10f, 20f, 5f, 0x11223344)
        assertEquals(1, b.size)
        assertContentEquals(floatArrayOf(5f, 15f, 15f, 15f, 15f, 25f, 5f, 25f), b.quad(0))
        assertEquals(0x11223344, b.color(0))
    }

    @Test fun aFullBatchIgnoresMoreAndClearsForTheNextFrame() {
        val b = QuadBatch(1, 64f)
        b.add(0f, 0f, 1f, 0); b.add(5f, 5f, 1f, 0)
        assertEquals(1, b.size)
        b.clear()
        assertEquals(0, b.size)
    }
}
