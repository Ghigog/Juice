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
}

/** The trimmed category name, or null when blank so no stray category is created. */
fun categoryName(raw: String): String? = raw.trim().ifEmpty { null }
