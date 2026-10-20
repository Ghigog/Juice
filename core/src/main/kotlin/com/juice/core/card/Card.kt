package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem

/** A stored card. A sub-item card points at its Project through [parentId]. */
data class Card(
    val id: String,
    val type: ItemType,
    val title: String,
    val parentId: String? = null,
    val completed: Boolean = false,
)

/**
 * The cards a landed parse produces: the card itself, plus one linked Task card per sub-item when it is a Project.
 * [newId] supplies ids so the caller owns id generation.
 */
fun ParsedItem.toCards(id: String, newId: () -> String): List<Card> {
    val card = Card(id, type, title)
    if (type != ItemType.Project) return listOf(card)
    return listOf(card) + subItems.map { Card(newId(), ItemType.Task, it, parentId = id) }
}

/** The user's override of a card's type; it always wins. */
fun Card.withType(type: ItemType): Card = copy(type = type)

/** Ticks [id]; when that completes every sub-item of its Project, the Project completes too. */
fun List<Card>.complete(id: String): List<Card> {
    val ticked = map { if (it.id == id) it.copy(completed = true) else it }
    val parentId = ticked.firstOrNull { it.id == id }?.parentId ?: return ticked
    val siblings = ticked.filter { it.parentId == parentId }
    if (!siblings.all { it.completed }) return ticked
    return ticked.map { if (it.id == parentId) it.copy(completed = true) else it }
}
