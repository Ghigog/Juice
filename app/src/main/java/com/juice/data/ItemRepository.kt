package com.juice.data

import com.juice.core.intake.IntakeCard
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ParsedItem
import com.juice.core.intake.mergeParse
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ItemRepository(private val dao: ItemDao) {
    suspend fun save(item: Item) = dao.upsert(item.toEntity())

    suspend fun get(id: String): Item? = dao.get(id)?.toItem()

    /** Items still to do: completed and archived ones are left out. */
    suspend fun active(): List<Item> = dao.active().map { it.toItem() }

    /** Active items, re-emitted whenever they change. */
    fun observeActive(): Flow<List<Item>> = dao.observeActive().map { list -> list.map { it.toItem() } }

    /** Marks [field] as changed by the user, so a later parse leaves it alone. */
    suspend fun markEdited(id: String, field: IntakeField) {
        get(id)?.let { save(it.copy(editedFields = it.editedFields + field)) }
    }

    /** Applies a parse to the saved item, keeping what the user edited (and always the title). */
    suspend fun applyParse(id: String, parsed: ParsedItem) {
        val item = get(id) ?: return
        val card = IntakeCard(item.type, item.title, deadline = item.deadline, editedFields = item.editedFields)
        val merged = card.mergeParse(parsed)
        save(item.copy(type = merged.type, deadline = merged.deadline))
    }

    suspend fun complete(id: String) = setStatus(id, ItemStatus.Completed)

    suspend fun archive(id: String) = setStatus(id, ItemStatus.Archived)

    private suspend fun setStatus(id: String, status: ItemStatus) {
        get(id)?.let { save(it.copy(status = status)) }
    }
}
