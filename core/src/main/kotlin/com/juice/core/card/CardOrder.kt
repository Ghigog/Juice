package com.juice.core.card

import com.juice.core.model.Item
import java.time.Instant

/** How the carousel orders cards: by creation date, or the order the user dragged them into. */
enum class CardOrder { Newest, Oldest, Custom }

/**
 * Starred cards stay first in their current order; the rest follow [order].
 * A card with no [Item.createdAt] counts as the oldest. The sort is stable, so ties keep their incoming order.
 */
fun List<Item>.sortedFor(order: CardOrder): List<Item> {
    if (order == CardOrder.Custom) return this
    val (starred, rest) = partition { it.starredAt != null }
    val created = compareBy<Item> { it.createdAt ?: Instant.MIN }
    return starred + rest.sortedWith(if (order == CardOrder.Newest) created.reversed() else created)
}
