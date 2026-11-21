package com.juice.data

import com.juice.core.category.Category
import com.juice.core.category.CategoryPalette
import com.juice.core.category.categoryName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The person's categories (named lists of cards), Main first. */
class CategoryRepository(
    private val dao: CategoryDao,
    private val newId: () -> String,
) {
    private val writes = Mutex()

    /** Every category in order; Main is always first, even in a database that has no row for it yet. */
    fun observe(): Flow<List<Category>> = dao.observe().map { rows ->
        val categories = rows.map { it.toCategory() }
        if (categories.any { it.id == Category.MAIN_ID }) categories
        else listOf(Category(Category.MAIN_ID, Category.MAIN_NAME)) + categories
    }

    /** Adds a category after the last one; null (and nothing added) for a blank [name]. */
    suspend fun create(name: String): Category? = writes.withLock {
        val clean = categoryName(name) ?: return@withLock null
        val colour = CategoryPalette.firstUnused(dao.all().mapNotNull { it.colour }).accent
        val category = Category(newId(), clean, colour)
        dao.upsert(CategoryEntity(category.id, category.name, (dao.maxPosition() ?: 0) + 1, colour))
        category
    }

    /** Renames [id]; Main keeps its name, and a blank [name] changes nothing. */
    suspend fun rename(id: String, name: String) = writes.withLock {
        if (id == Category.MAIN_ID) return@withLock
        categoryName(name)?.let { dao.rename(id, it) }
    }

    /** Saves [colour], a palette accent, for [id]; Main gets its row (name, position 0) when it has none yet. */
    suspend fun setColour(id: String, colour: Int) = writes.withLock {
        if (CategoryPalette.byAccent(colour) == null) return@withLock
        if (id == Category.MAIN_ID && dao.all().none { it.id == id }) {
            dao.upsert(CategoryEntity(Category.MAIN_ID, Category.MAIN_NAME, 0, colour))
        } else {
            dao.setColour(id, colour)
        }
    }

    /**
     * Saves the order [ids] give: Main at 0 whatever the input, unknown ids ignored,
     * and any category the list leaves out keeps its place after the listed ones.
     */
    suspend fun reorder(ids: List<String>) = writes.withLock {
        val known0 = dao.all().map { it.id }
        val known = known0.toSet()
        val listed = ids.filter { it in known && it != Category.MAIN_ID }.distinct()
        val rest = known0.filter { it != Category.MAIN_ID && it !in listed }
        (listOf(Category.MAIN_ID) + listed + rest).forEachIndexed { position, id -> dao.setPosition(id, position) }
    }
}
