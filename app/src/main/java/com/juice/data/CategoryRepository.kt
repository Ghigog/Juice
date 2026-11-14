package com.juice.data

import com.juice.core.category.Category
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
        val category = Category(newId(), clean)
        dao.upsert(CategoryEntity(category.id, category.name, (dao.maxPosition() ?: 0) + 1))
        category
    }

    /** Renames [id]; Main keeps its name, and a blank [name] changes nothing. */
    suspend fun rename(id: String, name: String) = writes.withLock {
        if (id == Category.MAIN_ID) return@withLock
        categoryName(name)?.let { dao.rename(id, it) }
    }
}
