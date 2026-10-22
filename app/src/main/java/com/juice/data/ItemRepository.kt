package com.juice.data

import com.juice.core.card.toCard
import com.juice.core.intake.IntakeCard
import com.juice.core.intake.IntakeField
import com.juice.core.intake.IntakeValidator
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.intake.mergeParse
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ItemRepository(private val dao: ItemDao) {
    /** Writes go one at a time, first come first served, so a quick complete then undo lands in that order. */
    private val writes = Mutex()

    /** Saves [item]; an item already stored keeps its place in the order, a new one goes last. */
    suspend fun save(item: Item) = writes.withLock {
        val position = dao.get(item.id)?.position ?: ((dao.maxPosition() ?: 0) + 1)
        dao.upsert(item.toEntity(position))
    }

    /** Stores the order the user chose: [ids] first to last. */
    suspend fun reorder(ids: List<String>) = writes.withLock {
        val base = (dao.maxPosition() ?: 0) + 1
        ids.forEachIndexed { index, id -> dao.setPosition(id, base + index) }
    }

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
        val card = IntakeCard(
            item.type, item.title, item.completionCondition, item.deadline, editedFields = item.editedFields,
        )
        val merged = card.mergeParse(parsed)
        save(item.copy(type = merged.type, deadline = merged.deadline, completionCondition = merged.completionCondition))
    }

    /**
     * Applies a landed parse to [id]'s card; a Project also gets its sub-items as steps. A card that
     * already has steps (from an earlier parse) keeps just those.
     */
    suspend fun land(id: String, parsed: ParsedItem, newId: () -> String = { UUID.randomUUID().toString() }): Item? {
        applyParse(id, parsed)
        val item = get(id) ?: return null
        if (item.steps.isNotEmpty()) return item
        val steps = if (item.type == ItemType.Project) parsed.toItems(id, newId).first().steps else emptyList()
        return item.copy(steps = steps).also { if (steps.isNotEmpty()) save(it) }
    }

    suspend fun complete(id: String) = setStatus(id, ItemStatus.Completed)

    suspend fun archive(id: String) = setStatus(id, ItemStatus.Archived)

    private suspend fun setStatus(id: String, status: ItemStatus) {
        get(id)?.let { save(it.copy(status = status)) }
    }
}

/** The item a landed parse produces; a Project keeps at most [IntakeValidator.MAX_SUB_ITEMS] sub-items as steps. */
fun ParsedItem.toItems(id: String, newId: () -> String): List<Item> {
    val card = copy(subItems = subItems.take(IntakeValidator.MAX_SUB_ITEMS)).toCard(id, newId)
    return listOf(
        Item(card.id, card.type, card.title, deadline = deadline, completionCondition = completionCondition, steps = card.steps),
    )
}
