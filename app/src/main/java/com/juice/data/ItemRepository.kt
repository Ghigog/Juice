package com.juice.data

import com.juice.core.model.Item
import com.juice.core.model.ItemStatus

class ItemRepository(private val dao: ItemDao) {
    suspend fun save(item: Item) = dao.upsert(item.toEntity())

    suspend fun get(id: String): Item? = dao.get(id)?.toItem()

    /** Items still to do: completed and archived ones are left out. */
    suspend fun active(): List<Item> = dao.active().map { it.toItem() }

    suspend fun complete(id: String) = setStatus(id, ItemStatus.Completed)

    suspend fun archive(id: String) = setStatus(id, ItemStatus.Archived)

    private suspend fun setStatus(id: String, status: ItemStatus) {
        get(id)?.let { save(it.copy(status = status)) }
    }
}
