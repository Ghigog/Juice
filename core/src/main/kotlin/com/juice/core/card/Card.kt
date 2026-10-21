package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Step

/** A stored card. A Project keeps its [steps] inside the card. */
data class Card(
    val id: String,
    val type: ItemType,
    val title: String,
    val steps: List<Step> = emptyList(),
    val completed: Boolean = false,
)

/** The card a landed parse produces: a Project gets one step per sub-item. [newId] supplies step ids. */
fun ParsedItem.toCard(id: String, newId: () -> String): Card {
    val steps = if (type == ItemType.Project) subItems.map { Step(newId(), it) } else emptyList()
    return Card(id, type, title, steps)
}

/** The user's override of a card's type; it always wins. */
fun Card.withType(type: ItemType): Card = copy(type = type)

/** Steps not yet ticked. */
fun List<Step>.open(): List<Step> = filterNot { it.done }

/** Ticks step [stepId]; when that was the last open step, the Project completes too. */
fun Card.completeStep(stepId: String): Card {
    val ticked = steps.map { if (it.id == stepId) it.copy(done = true) else it }
    return copy(steps = ticked, completed = completed || (ticked.isNotEmpty() && ticked.open().isEmpty()))
}

/** Crosses off step [stepId]: it is removed from the Project and does not count towards completing it. */
fun Card.removeStep(stepId: String): Card = copy(steps = steps.filterNot { it.id == stepId })
