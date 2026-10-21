package com.juice.data

import com.juice.core.card.toCards
import com.juice.core.intake.IntakeValidator
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.util.UUID

class ItemRepository(private val dao: ItemDao) {
    suspend fun save(item: Item) = dao.upsert(item.toEntity())

    suspend fun get(id: String): Item? = dao.get(id)?.toItem()

    /** Items still to do: completed and archived ones are left out. */
    suspend fun active(): List<Item> = dao.active().map { it.toItem() }

    /** Saves a landed parse as [id]'s card, plus one linked Task card per sub-item when it is a Project. */
    suspend fun land(id: String, parsed: ParsedItem, newId: () -> String = { UUID.randomUUID().toString() }): List<Item> =
        parsed.toItems(id, newId).also { items -> items.forEach { save(it) } }

    suspend fun complete(id: String) = setStatus(id, ItemStatus.Completed)

    suspend fun archive(id: String) = setStatus(id, ItemStatus.Archived)

    private suspend fun setStatus(id: String, status: ItemStatus) {
        get(id)?.let { save(it.copy(status = status)) }
    }
}

/** The items a landed parse produces; a Project keeps at most [IntakeValidator.MAX_SUB_ITEMS] sub-items. */
fun ParsedItem.toItems(id: String, newId: () -> String): List<Item> =
    copy(subItems = subItems.take(IntakeValidator.MAX_SUB_ITEMS)).toCards(id, newId).map { card ->
        Item(
            card.id,
            card.type,
            card.title,
            deadline = deadline.takeIf { card.id == id },
            parentId = card.parentId,
        )
    }
