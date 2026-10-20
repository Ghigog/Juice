package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardTest {
    private fun ids() = generateSequence(1) { it + 1 }.map { "c$it" }.iterator().let { { it.next() } }

    @Test
    fun projectWithThreeSubItemsYieldsThreeLinkedCards() {
        val cards = ParsedItem(ItemType.Project, "Move", subItems = listOf("a", "b", "c")).toCards("p", ids())
        val children = cards.filter { it.parentId == "p" }
        assertEquals(3, children.size)
        assertEquals(4, cards.size)
    }

    @Test
    fun lastSubItemCompletesProject() {
        var cards = ParsedItem(ItemType.Project, "Move", subItems = listOf("a", "b")).toCards("p", ids())
        cards = cards.complete("c1")
        assertFalse(cards.first { it.id == "p" }.completed)
        cards = cards.complete("c2")
        assertTrue(cards.first { it.id == "p" }.completed)
    }

    @Test
    fun typeOverrideSavesGoal() {
        assertEquals(ItemType.Goal, Card("x", ItemType.Task, "t").withType(ItemType.Goal).type)
    }
}
