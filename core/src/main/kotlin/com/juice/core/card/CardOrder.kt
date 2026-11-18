package com.juice.core.card

import com.juice.core.model.Item
import java.time.Instant

/** How the carousel orders cards: by creation date, or the order the user dragged them into. */
enum class CardOrder { Newest, Oldest, Custom }

/**
 * Starred cards stay first in their current order; the rest follow [order]: cards with a deadline, closest first,
 * then cards with no deadline. Creation date (newest or oldest per [order]) breaks deadline ties and orders the
 * cards with no deadline. A card with no [Item.createdAt] counts as the oldest. [CardOrder.Custom] returns the list
 * unchanged. The sort is stable, so full ties keep their incoming order.
 */
fun List<Item>.sortedFor(order: CardOrder): List<Item> {
    if (order == CardOrder.Custom) return this
    val (starred, rest) = partition { it.starredAt != null }
    val created = compareBy<Item> { it.createdAt ?: Instant.MIN }
    val byCreated = if (order == CardOrder.Newest) created.reversed() else created
    val byDeadline = compareBy<Item> { it.deadline == null }.thenBy { it.deadline ?: Instant.MAX }
    return starred + rest.sortedWith(byDeadline.then(byCreated))
}
