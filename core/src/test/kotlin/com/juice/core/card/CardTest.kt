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
    fun projectWithThreeSubItemsYieldsOneCardWithThreeSteps() {
        val card = ParsedItem(ItemType.Project, "Move", subItems = listOf("a", "b", "c")).toCard("p", ids())
        assertEquals(listOf("a", "b", "c"), card.steps.map { it.title })
        assertEquals(listOf("c1", "c2", "c3"), card.steps.map { it.id })
    }

    @Test
    fun nonProjectGetsNoSteps() {
        assertTrue(ParsedItem(ItemType.Task, "Call", subItems = listOf("a")).toCard("t", ids()).steps.isEmpty())
    }

    @Test
    fun lastStepCompletesProjectAndKeepsSteps() {
        var card = ParsedItem(ItemType.Project, "Move", subItems = listOf("a", "b")).toCard("p", ids())
        card = card.completeStep("c1")
        assertFalse(card.completed)
        card = card.completeStep("c2")
        assertTrue(card.completed)
        assertEquals(listOf(true, true), card.steps.map { it.done })
    }

    @Test
    fun removingAStepDropsItWithoutCompleting() {
        val card = ParsedItem(ItemType.Project, "Move", subItems = listOf("a", "b")).toCard("p", ids())
            .removeStep("c1")
        assertEquals(listOf("b"), card.steps.map { it.title })
        assertFalse(card.completed)
        assertTrue(card.completeStep("c2").completed)
    }

    @Test
    fun typeOverrideSavesGoal() {
        assertEquals(ItemType.Goal, Card("x", ItemType.Task, "t").withType(ItemType.Goal).type)
    }
}
