package com.juice.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.core.card.Card
import com.juice.core.card.completeStep
import com.juice.core.card.open
import com.juice.core.card.removeStep
import com.juice.core.gesture.GesturePoint
import com.juice.core.intake.IntakeField
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.data.ItemRepository
import com.juice.data.toItems
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The last ✓/✗, kept for [OverlayShellState.UNDO_WINDOW_MILLIS] so it can be taken back. */
data class Undo(val item: Item, val dismissed: Boolean)

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
                items = known + added
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

    /** Tapping the type pill steps Note, Task, Goal, Project and round again. A Routine needs a cadence, so the pill never makes one. */
    fun cycleType(item: Item) {
        setType(item, PILL_TYPES[(PILL_TYPES.indexOf(item.type) + 1) % PILL_TYPES.size])
    }

    /** The steps of [project] still to do. */
    fun stepsOf(project: Item): List<Step> = project.steps.open()

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

    /** Swaps the captured card [id] for what its parse produced: the card, with a Project's sub-items as steps. */
    fun land(id: String, parsed: ParsedItem) {
        val landed = parsed.toItems(id, newId).first()
        val at = items.indexOfFirst { it.id == id }
        if (at < 0) return
        items = items.take(at) + landed + items.drop(at + 1)
        scope.launch { repository.save(landed) }
    }

    /** Completing a Project's card. False when [canComplete] is not met. */
    fun complete(item: Item): Boolean {
        if (!canComplete(item)) return false
        finish(item, ItemStatus.Completed, dismissed = false)
        onCompleted(item)
        return true
    }

    /** Ticks a step of [project]; ticking the last open one completes the Project. */
    fun completeStep(project: Item, stepId: String) {
        val card = project.toCard().completeStep(stepId)
        if (!card.completed) return updateSteps(project, card.steps)
        // Undo takes the Project back with its last step still open.
        finish(project.copy(steps = card.steps), ItemStatus.Completed, dismissed = false, restore = project)
        onCompleted(project)
    }

    /** Crosses off a step of [project]: it is removed from the card. */
    fun removeStep(project: Item, stepId: String) = updateSteps(project, project.toCard().removeStep(stepId).steps)

    private fun updateSteps(project: Item, steps: List<Step>) {
        val changed = project.copy(steps = steps)
        items = items.map { if (it.id == project.id) changed else it }
        scope.launch { repository.save(changed) }
    }

    private fun Item.toCard() = Card(id, type, title, steps)

    fun dismiss(item: Item) = finish(item, ItemStatus.Archived, dismissed = true)

    /** Puts the undone item back where it was in the carousel. */
    fun undoLast() {
        val pending = undo ?: return
        undo = null
        if (!pending.dismissed) onCompletionUndone()
        items = (items + pending.item).sortedBy { order[it.id] ?: Int.MAX_VALUE }
        // Undispatched, so it queues for the write lock right after the save it undoes.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(pending.item) }
    }

    fun expireUndo(expired: Undo) {
        if (undo == expired) undo = null
    }

    private val order = HashMap<String, Int>()

    private fun finish(item: Item, status: ItemStatus, dismissed: Boolean, restore: Item = item) {
        items.forEachIndexed { index, it -> order[it.id] = index }
        items = items.filterNot { it.id == item.id }
        undo = Undo(restore, dismissed)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(item.copy(status = status)) }
    }

    companion object {
        const val UNDO_WINDOW_MILLIS = 10_000L

        /** How long a card shows that a cloud parse is running before giving up on showing it. */
        const val PARSE_PATIENCE_MILLIS = 15_000L

        private val PILL_TYPES = listOf(ItemType.Note, ItemType.Task, ItemType.Goal, ItemType.Project)
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
