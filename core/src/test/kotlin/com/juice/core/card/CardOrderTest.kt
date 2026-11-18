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

    private fun due(id: String, deadline: String?, created: String = "1", starred: String? = null) =
        card(id, created, starred).copy(deadline = deadline?.let { Instant.parse("2026-03-${it}T09:00:00Z") })

    @Test
    fun closerDeadlinesComeFirstThenCardsWithNone() {
        val friday = due("fri", "06")
        val tuesday = due("tue", "03")
        val none = due("none", null, created = "3")
        val sorted = listOf(none, friday, tuesday).sortedFor(CardOrder.Newest)
        assertEquals(listOf("tue", "fri", "none"), ids(sorted))
        assertEquals(listOf("tue", "fri", "none"), ids(listOf(none, friday, tuesday).sortedFor(CardOrder.Oldest)))
    }

    @Test
    fun creationBreaksDeadlineTiesPerOrder() {
        val old = due("old", "03", created = "1")
        val new = due("new", "03", created = "2")
        assertEquals(listOf("new", "old"), ids(listOf(old, new).sortedFor(CardOrder.Newest)))
        assertEquals(listOf("old", "new"), ids(listOf(new, old).sortedFor(CardOrder.Oldest)))
    }

    @Test
    fun starredStayFirstEvenWithoutADeadline() {
        val star = due("star", null, starred = "1")
        val soon = due("soon", "03", created = "2")
        val soonOld = due("soonOld", "03", created = "1")
        assertEquals(listOf("star", "soonOld", "soon"), ids(listOf(soon, soonOld, star).sortedFor(CardOrder.Oldest)))
    }

    @Test
    fun customIgnoresDeadlines() {
        val list = listOf(due("late", "09"), due("soon", "03"))
        assertEquals(listOf("late", "soon"), ids(list.sortedFor(CardOrder.Custom)))
    }
}
