package com.juice.ui.overlay

import com.juice.core.economy.ItemType as EconomyItemType
import com.juice.core.intake.ItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTypeEconomyTest {
    @Test fun eachItemTypeMapsToItsEconomyItemType() {
        val expected = mapOf(
            ItemType.Note to EconomyItemType.NOTE,
            ItemType.Task to EconomyItemType.TASK,
            ItemType.List to EconomyItemType.LIST,
            ItemType.Goal to EconomyItemType.GOAL,
            ItemType.Project to EconomyItemType.PROJECT,
            ItemType.Routine to EconomyItemType.ROUTINE,
        )
        assertEquals(ItemType.entries.toSet(), expected.keys)
        expected.forEach { (type, economy) -> assertEquals(economy, type.toEconomy()) }
    }
}
