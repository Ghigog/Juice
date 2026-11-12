package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Step

/** A stored card. Cards saved before steps were retired may still carry [steps]. */
data class Card(
    val id: String,
    val type: ItemType,
    val title: String,
    val steps: List<Step> = emptyList(),
    val completed: Boolean = false,
)

/** The card a landed parse produces. Sub-items decide the type but are not stored: the person's own text is the list. */
fun ParsedItem.toCard(id: String): Card = Card(id, type, title)

/** The user's override of a card's type; it always wins. */
fun Card.withType(type: ItemType): Card = copy(type = type)
