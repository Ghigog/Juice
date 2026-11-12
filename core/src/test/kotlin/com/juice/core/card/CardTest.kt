package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardTest {
    @Test
    fun projectAndListKeepNoStepsFromTheirSubItems() {
        listOf(ItemType.Project, ItemType.List, ItemType.Task).forEach { type ->
            assertTrue(ParsedItem(type, "Move", subItems = listOf("a", "b", "c")).toCard("p").steps.isEmpty())
        }
    }

    @Test
    fun typeOverrideSavesGoal() {
        assertEquals(ItemType.Goal, Card("x", ItemType.Task, "t").withType(ItemType.Goal).type)
    }
}
