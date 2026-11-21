package com.juice.core.category

/** A named list of cards. */
data class Category(val id: String, val name: String) {
    companion object {
        const val MAIN_ID = "main"
        const val MAIN_NAME = "Main"
    }
}

/** The person's categories in their fixed order, Main first. Stepping never wraps. */
data class Categories(val list: List<Category>) {
    /** Position of [id] in the order, or -1 when unknown. */
    fun indexOf(id: String): Int = list.indexOfFirst { it.id == id }

    /** The category after [id], or null on the last (or an unknown id). */
    fun next(id: String): Category? {
        val i = indexOf(id)
        return if (i < 0) null else list.getOrNull(i + 1)
    }

    /** The category before [id], or null on the first (or an unknown id). */
    fun previous(id: String): Category? {
        val i = indexOf(id)
        return if (i < 0) null else list.getOrNull(i - 1)
    }

    fun isLast(id: String): Boolean {
        val i = indexOf(id)
        return i >= 0 && i == list.lastIndex
    }

    /** Whether [id] can be reordered: not Main, not unknown, and there is somewhere to go. */
    fun canMove(id: String): Boolean = id != Category.MAIN_ID && indexOf(id) >= 0 && list.size > 2

    /**
     * Moves [id] to [toIndex], clamped into 1..lastIndex so nothing ever lands before Main.
     * Main and unknown ids leave the order unchanged.
     */
    fun move(id: String, toIndex: Int): Categories {
        val from = indexOf(id)
        if (id == Category.MAIN_ID || from < 0) return this
        val target = toIndex.coerceIn(1, list.lastIndex)
        val reordered = list.toMutableList()
        reordered.add(target, reordered.removeAt(from))
        return Categories(reordered)
    }

    /** One place earlier; stays put when already right after Main. */
    fun moveEarlier(id: String): Categories = move(id, indexOf(id) - 1)

    /** One place later; stays put when already last. */
    fun moveLater(id: String): Categories = move(id, indexOf(id) + 1)
}

/** The trimmed category name, or null when blank so no stray category is created. */
fun categoryName(raw: String): String? = raw.trim().ifEmpty { null }
