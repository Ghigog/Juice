package com.juice.core.economy

/** A completion that remembers which category earned it, so undo touches that category only. */
data class CategoryCompletion(val categoryId: String, val completion: Completion)

/**
 * One [EconomyState] (Juice and Thirst Meter) per category id, built on [Economy].
 * A category that was never touched reads as an empty [EconomyState].
 */
class CategoryEconomy(
    private val economy: Economy = Economy(),
    val states: Map<String, EconomyState> = emptyMap(),
) {
    operator fun get(categoryId: String): EconomyState = states[categoryId] ?: EconomyState()

    fun multiplier(categoryId: String): Double = economy.multiplier(get(categoryId).meter)

    val totalJuice: Long get() = states.values.sumOf { it.juice }

    fun complete(categoryId: String, type: ItemType): Pair<CategoryEconomy, CategoryCompletion> {
        val (next, completion) = economy.complete(get(categoryId), type)
        return with(categoryId, next) to CategoryCompletion(categoryId, completion)
    }

    fun undo(completion: CategoryCompletion): CategoryEconomy =
        with(completion.categoryId, economy.undo(get(completion.categoryId), completion.completion))

    fun drain(minutes: Double): CategoryEconomy =
        CategoryEconomy(economy, states.mapValues { (_, state) -> economy.drain(state, minutes) })

    private fun with(categoryId: String, state: EconomyState) =
        CategoryEconomy(economy, states + (categoryId to state))
}
