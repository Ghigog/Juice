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
import com.juice.core.routine.Cadence
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ItemRepository(private val dao: ItemDao) {
    /** Writes run one at a time, in the order they were asked for, so a quick complete then undo lands in order. */
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
    suspend fun active(today: LocalDate = LocalDate.now()): List<Item> =
        dao.active(today.toEpochDay()).map { it.toItem() }

    /** Active items, re-emitted whenever they change. */
    fun observeActive(today: LocalDate = LocalDate.now()): Flow<List<Item>> =
        dao.observeActive(today.toEpochDay()).map { list -> list.map { it.toItem() } }

    /** Marks [field] as changed by the user, so a later parse leaves it alone. */
    suspend fun markEdited(id: String, field: IntakeField) {
        get(id)?.let { save(it.copy(editedFields = it.editedFields + field)) }
    }

    /** Applies a parse to the saved item, keeping what the user edited (and always the title). */
    suspend fun applyParse(id: String, parsed: ParsedItem, today: LocalDate = LocalDate.now()) {
        val item = get(id) ?: return
        val card = IntakeCard(
            item.type, item.title, item.completionCondition, item.deadline, editedFields = item.editedFields,
        )
        val merged = card.mergeParse(parsed)
        val landed = item.copy(type = merged.type, deadline = merged.deadline, completionCondition = merged.completionCondition)
        save(merged.cadence?.takeIf { merged.type == ItemType.Routine }?.let { landed.asRoutine(it, today) } ?: landed)
    }

    /**
     * Applies a landed parse to [id]'s card; a Project also gets its sub-items as steps. A card that
     * already has steps (from an earlier parse) keeps just those.
     */
    suspend fun land(
        id: String,
        parsed: ParsedItem,
        today: LocalDate = LocalDate.now(),
        newId: () -> String = { UUID.randomUUID().toString() },
    ): Item? {
        applyParse(id, parsed, today)
        val item = get(id) ?: return null
        if (item.steps.isNotEmpty()) return item
        val steps = if (item.type == ItemType.Project) parsed.toItems(id, newId).first().steps else emptyList()
        return item.copy(steps = steps).also { if (steps.isNotEmpty()) save(it) }
    }

    /** Completing or skipping a Routine's occurrence: it moves on to its next due day and stays Active. */
    suspend fun advanceRoutine(id: String, today: LocalDate = LocalDate.now()) {
        get(id)?.let { save(it.advance(today)) }
    }

    suspend fun complete(id: String) = setStatus(id, ItemStatus.Completed)

    suspend fun archive(id: String) = setStatus(id, ItemStatus.Archived)

    private suspend fun setStatus(id: String, status: ItemStatus) {
        get(id)?.let { save(it.copy(status = status)) }
    }
}

/** The item a landed parse produces; a Project keeps at most [IntakeValidator.MAX_SUB_ITEMS] sub-items as steps. */
fun ParsedItem.toItems(id: String, newId: () -> String, today: LocalDate = LocalDate.now()): List<Item> {
    val card = copy(subItems = subItems.take(IntakeValidator.MAX_SUB_ITEMS)).toCard(id, newId)
    val item = Item(card.id, card.type, card.title, deadline = deadline, completionCondition = completionCondition, steps = card.steps)
    return listOf(if (card.type == ItemType.Routine && cadence != null) item.asRoutine(cadence!!, today) else item)
}

/** This item as a Routine on [cadence], first due on or after [today], with the deadline of that day. */
internal fun Item.asRoutine(cadence: Cadence, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Item {
    val due = cadence.firstDueFrom(today)
    return copy(type = ItemType.Routine, cadence = cadence, nextDueOn = due, deadline = cadence.deadlineOn(due, zone))
}

/** A Routine moved to the first due day after [today] (or after its own due day if that is later), so a missed occurrence never piles up. */
fun Item.advance(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Item {
    val cadence = cadence ?: return this
    val due = cadence.nextDueAfter(maxOf(nextDueOn ?: today, today))
    return copy(nextDueOn = due, deadline = cadence.deadlineOn(due, zone), status = ItemStatus.Active)
}
