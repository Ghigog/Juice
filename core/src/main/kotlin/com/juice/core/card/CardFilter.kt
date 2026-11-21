package com.juice.core.card

import com.juice.core.intake.ItemType
import com.juice.core.model.Item

/**
 * Narrows the carousel. A card matches when its type is one of [types] (any type when empty) and its title contains
 * the trimmed [query], ignoring case (any title when the query is null or blank). Only titles are searched.
 */
data class CardFilter(
    val types: Set<ItemType> = emptySet(),
    val query: String? = null,
) {
    private val needle: String = query?.trim().orEmpty()

    val isActive: Boolean get() = types.isNotEmpty() || needle.isNotEmpty()

    fun matches(item: Item): Boolean =
        (types.isEmpty() || item.type in types) &&
            (needle.isEmpty() || item.title.contains(needle, ignoreCase = true))
}

/** The cards that match [filter], in their incoming order. */
fun List<Item>.filteredBy(filter: CardFilter): List<Item> = if (filter.isActive) filter { filter.matches(it) } else this
