package com.juice.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.core.card.Card
import com.juice.core.card.complete
import com.juice.core.gesture.GesturePoint
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import com.juice.data.toItems
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The last ✓/✗, kept for [OverlayShellState.UNDO_WINDOW_MILLIS] so it can be taken back.
 * [project] is the Project that completed along with a last sub-item.
 */
data class Undo(val item: Item, val dismissed: Boolean, val project: Item? = null)

/** State of the overlay: the Active items shown in the carousel and the pending undo. */
class OverlayShellState(
    private val repository: ItemRepository,
    private val scope: CoroutineScope,
    initial: List<Item> = emptyList(),
    private val onCompleted: (Item) -> Unit = {},
    private val onCompletionUndone: () -> Unit = {},
    /** Hands a saved capture to intake; true means a cloud parse is on its way. */
    private val onCaptured: suspend (Item) -> Boolean = { false },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var items by mutableStateOf(initial)
        private set
    var undo by mutableStateOf<Undo?>(null)
        private set

    /** Id of the card picked up to be moved, if any. */
    var held by mutableStateOf<String?>(null)
        private set

    /** Index the held card would take if dropped now: that of the card at the focal dot. */
    var target by mutableStateOf(0)
        private set

    /** Ids of captured cards whose cloud parse has not landed yet. */
    var parsing by mutableStateOf(emptySet<String>())
        private set

    /** Shows a new Note card at once (first in the carousel) and saves it in the background. */
    fun capture(text: String): Item? {
        val title = text.trim()
        if (title.isEmpty()) return null
        val note = Item(newId(), ItemType.Note, title)
        items = listOf(note) + items
        scope.launch {
            repository.save(note)
            if (onCaptured(note)) {
                parsing = parsing + note.id
                delay(PARSE_PATIENCE_MILLIS)
                parsing = parsing - note.id
            }
        }
        return note
    }

    /** Keeps the cards in step with the repository, so a parse landing later updates an open card. */
    fun observe() {
        scope.launch {
            repository.observeActive().collect { saved ->
                val byId = saved.associateBy { it.id }
                val landed = parsing.filter { id ->
                    val before = items.firstOrNull { it.id == id }
                    val after = byId[id]
                    after != null && (after.type != before?.type || after.deadline != before.deadline)
                }
                if (landed.isNotEmpty()) parsing = parsing - landed.toSet()
                val known = items.mapNotNull { byId[it.id] }
                val added = saved.filter { s -> items.none { it.id == s.id } }
                // New sub-item cards go right after their Project; anything else new goes last.
                items = known.flatMap { k -> listOf(k) + added.filter { it.parentId == k.id } } +
                    added.filter { a -> known.none { it.id == a.parentId } }
            }
        }
    }

    /** Picks [item] up to move it; the list keeps scrolling while it is held. */
    fun pickUp(item: Item, at: Int = items.indexOfFirst { it.id == item.id }) {
        if (at < 0) return
        held = item.id
        target = at
    }

    /** Where the held card would land: the index of the card at the focal dot. */
    fun moveTarget(index: Int) {
        target = index.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    }

    /** Puts the held card down without moving it. */
    fun cancelHold() {
        held = null
    }

    /** Moves the held card to the target index and saves the order. False when the order is unchanged. */
    fun drop(): Boolean {
        val card = items.firstOrNull { it.id == held }
        held = null
        card ?: return false
        val rest = items.filterNot { it.id == card.id }
        val reordered = rest.take(target) + card + rest.drop(target)
        if (reordered == items) return false
        items = reordered
        scope.launch { repository.reorder(reordered.map { it.id }) }
        return true
    }

    /** The user picks a type; a late parse will not change it. */
    fun setType(item: Item, type: ItemType) = edit(item.id, IntakeField.Type) { it.copy(type = type) }

    /** Tapping the type pill steps Note, Task, Goal, Project and round again. */
    fun cycleType(item: Item) {
        val types = ItemType.entries
        setType(item, types[(types.indexOf(item.type) + 1) % types.size])
    }

    /** Active sub-item cards of [project]. */
    fun stepsOf(project: Item): List<Item> = items.filter { it.parentId == project.id }

    /** A Project completes through its steps, so ticking it while steps are open does nothing. */
    fun canComplete(item: Item): Boolean = item.type != ItemType.Project || stepsOf(item).isEmpty()

    /** The user rewrites a title; it is saved as typed and a late parse never changes it. */
    fun setTitle(item: Item, title: String) {
        val edited = title.trim()
        if (edited.isEmpty() || edited == item.title) return
        edit(item.id, IntakeField.Title) { it.copy(title = edited) }
    }

    /** Applies a user edit on screen and saves the whole card in one write, so quick edits land in order. */
    private fun edit(id: String, field: IntakeField, change: (Item) -> Item) {
        val edited = items.firstOrNull { it.id == id }?.let { change(it).copy(editedFields = it.editedFields + field) } ?: return
        items = items.map { if (it.id == id) edited else it }
        scope.launch { repository.save(edited) }
    }

    /** Swaps the captured card [id] for what its parse produced: the card, and a Project's sub-item cards. */
    fun land(id: String, parsed: ParsedItem) {
        val landed = parsed.toItems(id, newId)
        val at = items.indexOfFirst { it.id == id }
        if (at < 0) return
        items = items.take(at) + landed + items.drop(at + 1)
        scope.launch { landed.forEach { repository.save(it) } }
    }

    /** Completing a Project's last active sub-item completes the Project too. False when [canComplete] is not met. */
    fun complete(item: Item): Boolean {
        if (!canComplete(item)) return false
        val project = completedProject(item)
        finish(item, ItemStatus.Completed, dismissed = false, project)
        onCompleted(item)
        if (project != null) onCompleted(project)
        return true
    }

    private fun completedProject(item: Item): Item? {
        val parentId = item.parentId ?: return null
        val cards = items.filter { it.id == parentId || it.parentId == parentId }
        if (cards.none { it.id == parentId }) return null
        val done = cards.map { Card(it.id, it.type, it.title, it.parentId) }.complete(item.id)
        return cards.first { it.id == parentId }.takeIf { p -> done.first { it.id == p.id }.completed }
    }

    fun dismiss(item: Item) = finish(item, ItemStatus.Archived, dismissed = true)

    /** Puts the undone item back where it was in the carousel. */
    fun undoLast() {
        val pending = undo ?: return
        undo = null
        val restored = listOfNotNull(pending.item, pending.project)
        if (!pending.dismissed) repeat(restored.size) { onCompletionUndone() }
        items = (items + restored).sortedBy { order[it.id] ?: Int.MAX_VALUE }
        scope.launch { restored.forEach { repository.save(it) } }
    }

    fun expireUndo(expired: Undo) {
        if (undo == expired) undo = null
    }

    private val order = HashMap<String, Int>()

    private fun finish(item: Item, status: ItemStatus, dismissed: Boolean, project: Item? = null) {
        items.forEachIndexed { index, it -> order[it.id] = index }
        items = items.filterNot { it.id == item.id || it.id == project?.id }
        undo = Undo(item, dismissed, project)
        scope.launch {
            repository.save(item.copy(status = status))
            project?.let { repository.save(it.copy(status = ItemStatus.Completed)) }
        }
    }

    companion object {
        const val UNDO_WINDOW_MILLIS = 10_000L

        /** How long a card shows that a cloud parse is running before giving up on showing it. */
        const val PARSE_PATIENCE_MILLIS = 15_000L
    }
}

enum class Swipe { LEFT, RIGHT }

/**
 * The direction of a mostly-horizontal swipe, or null when [stroke] is too short or too tall to be one
 * (a drawn ✓ or ✗ is as high as it is wide). [minDistance] is the least sideways travel.
 */
fun swipeOf(stroke: List<GesturePoint>, minDistance: Double): Swipe? {
    if (stroke.size < 2) return null
    val dx = stroke.last().x - stroke.first().x
    val height = stroke.maxOf { it.y } - stroke.minOf { it.y }
    if (abs(dx) < minDistance || height > abs(dx) * 0.35) return null
    return if (dx > 0) Swipe.RIGHT else Swipe.LEFT
}
