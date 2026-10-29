package com.juice.core.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    private val economy = Economy()

    @Test
    fun goalAtTwentyPercentEarnsBaseAndFillsToFifty() {
        val (state, c) = economy.complete(EconomyState(0, 20.0), ItemType.GOAL)
        assertEquals(100, c.earned)
        assertEquals(100, state.juice)
        assertEquals(50.0, state.meter, 1e-9)
    }

    @Test
    fun routineAtBaseMeterEarnsTwentyFiveXpAndFillsTwelvePercent() {
        val (state, c) = economy.complete(EconomyState(0, 20.0), ItemType.ROUTINE)
        assertEquals(25, c.earned)
        assertEquals(32.0, state.meter, 1e-9)
    }

    @Test
    fun taskAtEightyPercentEarnsEightTimes() {
        val (state, _) = economy.complete(EconomyState(0, 80.0), ItemType.TASK)
        assertEquals(200, state.juice)
    }

    @Test
    fun hydratedScalesLinearly() {
        assertEquals(2.0, economy.multiplier(26.0), 1e-9)
        assertEquals(3.0, economy.multiplier(50.5), 1e-9)
        assertEquals(4.0, economy.multiplier(75.0), 1e-9)
    }

    @Test
    fun thirstyEdgeIsOneTimes() {
        assertEquals(1.0, economy.multiplier(25.0), 1e-9)
    }

    @Test
    fun satiatedIsEightTimesJustAboveSeventyFiveUpToFull() {
        assertEquals(8.0, economy.multiplier(75.1), 1e-9)
        assertEquals(8.0, economy.multiplier(100.0), 1e-9)
    }

    @Test
    fun completionPastFullLeavesMeterAtOneHundred() {
        val (state, _) = economy.complete(EconomyState(0, 90.0), ItemType.GOAL)
        assertEquals(100.0, state.meter, 1e-9)
    }

    @Test
    fun aListIsWorthMoreThanATaskAndLessThanAProject() {
        val c = EconomyConfig()
        assertTrue(c.baseXp.getValue(ItemType.TASK) < c.baseXp.getValue(ItemType.LIST))
        assertTrue(c.baseXp.getValue(ItemType.LIST) < c.baseXp.getValue(ItemType.PROJECT))
        assertTrue(c.fillPercent.getValue(ItemType.TASK) < c.fillPercent.getValue(ItemType.LIST))
        assertTrue(c.fillPercent.getValue(ItemType.LIST) < c.fillPercent.getValue(ItemType.PROJECT))
    }

    @Test
    fun undoRestoresJuiceAndMeter() {
        val before = EconomyState(40, 80.0)
        val (after, c) = economy.complete(before, ItemType.PROJECT)
        assertEquals(100.0, after.meter, 1e-9)
        assertEquals(before, economy.undo(after, c))
    }

    @Test
    fun dismissChangesNothing() {
        val s = EconomyState(7, 33.0)
        assertEquals(s, economy.dismiss(s))
    }

    @Test
    fun drainsOnePercentPerHour() {
        assertEquals(49.0, economy.drain(EconomyState(0, 50.0), 60.0).meter, 1e-9)
        assertEquals(50.0 - 1.0 / 6, economy.drain(EconomyState(0, 50.0), 10.0).meter, 1e-9)
        assertEquals(0.0, economy.drain(EconomyState(0, 0.5), 31 * 60.0).meter, 1e-9)
    }
}
