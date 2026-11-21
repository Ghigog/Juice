package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardFilterTest {
    private fun card(id: String, type: ItemType, title: String = id) = Item(id, type, title)
    private fun ids(items: List<Item>) = items.map { it.id }

    @Test
    fun keepsCardsOfAnyFloatedTypeInOrder() {
        val cards = listOf(card("t", ItemType.Task), card("g", ItemType.Goal), card("n", ItemType.Note))
        val kept = cards.filteredBy(CardFilter(types = setOf(ItemType.Task, ItemType.Note)))
        assertEquals(listOf("t", "n"), ids(kept))
    }

    @Test
    fun queryIsTrimmedAndIgnoresCase() {
        val cards = listOf(card("a", ItemType.Task, "Buy Milk"), card("b", ItemType.Task, "Call mum"))
        assertEquals(listOf("a"), ids(cards.filteredBy(CardFilter(query = " milk "))))
    }

    @Test
    fun typesAndQueryBothMustMatch() {
        val cards = listOf(card("n", ItemType.Note, "milk"), card("t", ItemType.Task, "milk run"))
        assertEquals(listOf("t"), ids(cards.filteredBy(CardFilter(setOf(ItemType.Task), "milk"))))
    }

    @Test
    fun emptyFilterKeepsEverythingAndIsInactive() {
        val cards = listOf(card("a", ItemType.Task), card("b", ItemType.Goal))
        assertEquals(cards, cards.filteredBy(CardFilter()))
        assertFalse(CardFilter().isActive)
        assertFalse(CardFilter(query = "  ").isActive)
        assertTrue(CardFilter(query = "x").isActive)
    }
}
