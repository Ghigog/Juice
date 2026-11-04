package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CardOrderTest {
    private fun card(id: String, created: String? = null, starred: String? = null) = Item(
        id, ItemType.Note, id,
        createdAt = created?.let { Instant.parse("2026-01-0${it}T00:00:00Z") },
        starredAt = starred?.let { Instant.parse("2026-02-0${it}T00:00:00Z") },
    )

    private val jan1 = card("a", "1")
    private val jan2 = card("b", "2")
    private val jan3 = card("c", "3")
    private fun ids(items: List<Item>) = items.map { it.id }

    @Test
    fun newestPutsTheLatestCreatedFirst() =
        assertEquals(listOf("c", "b", "a"), ids(listOf(jan1, jan3, jan2).sortedFor(CardOrder.Newest)))

    @Test
    fun oldestPutsTheEarliestCreatedFirst() =
        assertEquals(listOf("a", "b", "c"), ids(listOf(jan3, jan1, jan2).sortedFor(CardOrder.Oldest)))

    @Test
    fun customLeavesTheListAlone() =
        assertEquals(listOf("b", "c", "a"), ids(listOf(jan2, jan3, jan1).sortedFor(CardOrder.Custom)))

    @Test
    fun starredCardsStayFirstInTheirCurrentOrder() {
        val s1 = card("s1", "1", starred = "2")
        val s2 = card("s2", "3", starred = "1")
        assertEquals(listOf("s1", "s2", "c", "a"), ids(listOf(s1, jan1, s2, jan3).sortedFor(CardOrder.Newest)))
    }

    @Test
    fun aCardWithoutACreationDateCountsAsTheOldest() {
        val undated = card("u")
        assertEquals(listOf("c", "u"), ids(listOf(undated, jan3).sortedFor(CardOrder.Newest)))
        assertEquals(listOf("u", "c"), ids(listOf(jan3, undated).sortedFor(CardOrder.Oldest)))
    }

    @Test
    fun tiesKeepTheirIncomingOrder() {
        val x = card("x", "2")
        val y = card("y", "2")
        assertEquals(listOf("x", "y"), ids(listOf(x, y).sortedFor(CardOrder.Newest)))
        assertEquals(listOf("y", "x"), ids(listOf(y, x).sortedFor(CardOrder.Oldest)))
    }
}
