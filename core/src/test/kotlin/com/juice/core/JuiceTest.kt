package com.juice.core

import org.junit.Assert.assertEquals
import org.junit.Test

class JuiceTest {
    @Test
    fun nameIsJuice() {
        assertEquals("Juice", Juice.NAME)
    }
}
