package com.juice.core.economy

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryEconomyTest {
    private fun start(vararg states: Pair<String, EconomyState>) = CategoryEconomy(states = mapOf(*states))

    @Test
    fun untouchedCategoryReadsEmpty() {
        val e = start("main" to EconomyState(500, 40.0))
        assertEquals(EconomyState(), e["game ideas"])
        assertEquals(1.0, e.multiplier("game ideas"))
    }

    @Test
    fun completingEarnsInThatCategoryOnly() {
        val e = start("main" to EconomyState(500, 50.0), "game ideas" to EconomyState(0, 10.0))
        val (next, c) = e.complete("game ideas", ItemType.TASK)
        assertEquals(EconomyState(25, 22.0), next["game ideas"])
        assertEquals(EconomyState(500, 50.0), next["main"])
        assertEquals(e.totalJuice + 25, next.totalJuice)
        assertEquals("game ideas", c.categoryId)
    }

    @Test
    fun undoRemovesFromThatCategoryOnly() {
        val e = start("main" to EconomyState(500, 50.0), "game ideas" to EconomyState(0, 10.0))
        val (next, c) = e.complete("game ideas", ItemType.TASK)
        val undone = next.undo(c)
        assertEquals(EconomyState(0, 10.0), undone["game ideas"])
        assertEquals(EconomyState(500, 50.0), undone["main"])
    }

    @Test
    fun drainReducesEveryMeter() {
        val e = start("main" to EconomyState(5, 30.0), "game ideas" to EconomyState(7, 10.0)).drain(150.0)
        assertEquals(20.0, e["main"].meter, 1e-9)
        assertEquals(0.0, e["game ideas"].meter, 1e-9)
        assertEquals(12, e.totalJuice)
    }
}
