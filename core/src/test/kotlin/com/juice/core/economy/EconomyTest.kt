package com.juice.core.economy

import kotlin.test.Test
import kotlin.test.assertEquals

class EconomyTest {
    private val economy = Economy()

    @Test
    fun `goal at 20 percent earns 100 Juice at 1x and lifts meter to 50 percent`() {
        val completion = economy.complete(EconomyState(juice = 0, meter = 20.0), ItemType.GOAL)

        assertEquals(1.0, completion.multiplier)
        assertEquals(100, completion.juiceEarned)
        assertEquals(EconomyState(juice = 100, meter = 50.0), completion.after)
    }

    @Test
    fun `task at 80 percent earns 200 Juice at 8x`() {
        val completion = economy.complete(EconomyState(meter = 80.0), ItemType.TASK)

        assertEquals(8.0, completion.multiplier)
        assertEquals(200, completion.juiceEarned)
    }

    @Test
    fun `hydrated multiplier scales linearly from 2x to 4x`() {
        val config = EconomyConfig()
        assertEquals(2.0, config.multiplierAt(25.0001), 0.001)
        assertEquals(3.0, config.multiplierAt(50.0), 1e-9)
        assertEquals(4.0, config.multiplierAt(75.0), 1e-9)
    }

    @Test
    fun `multiplier is read before the completion fills the meter`() {
        val completion = economy.complete(EconomyState(meter = 70.0), ItemType.PROJECT)

        assertEquals(3.8, completion.multiplier, 1e-9)
        assertEquals(1900, completion.juiceEarned)
        assertEquals(100.0, completion.after.meter)
    }

    @Test
    fun `undo returns Juice and meter to their prior values`() {
        val start = EconomyState(juice = 40, meter = 20.0)
        val completion = economy.complete(start, ItemType.GOAL)

        assertEquals(start, economy.undo(completion))
    }

    @Test
    fun `dismiss gives no Juice and no fill`() {
        val start = EconomyState(juice = 40, meter = 20.0)

        assertEquals(start, economy.dismiss(start))
    }

    @Test
    fun `meter drains 1 percent per minute and stops at zero`() {
        assertEquals(40.0, economy.drain(EconomyState(meter = 50.0), minutes = 10.0).meter)
        assertEquals(0.0, economy.drain(EconomyState(meter = 5.0), minutes = 10.0).meter)
    }

    @Test
    fun `values are tunable`() {
        val tuned = Economy(EconomyConfig(values = mapOf(ItemType.NOTE to ItemValue(baseXp = 7, fillPercent = 10.0))))

        val completion = tuned.complete(EconomyState(), ItemType.NOTE)

        assertEquals(7, completion.juiceEarned)
        assertEquals(10.0, completion.after.meter)
    }
}
