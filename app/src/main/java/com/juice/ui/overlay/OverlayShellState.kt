package com.juice.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.core.card.Card
import com.juice.core.card.complete
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import com.juice.data.toItems
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var items by mutableStateOf(initial)
        private set
    var undo by mutableStateOf<Undo?>(null)
        private set

    /** Shows a new Note card at once and saves it in the background. */
    fun capture(text: String) {
        val title = text.trim()
        if (title.isEmpty()) return
        val note = Item(newId(), ItemType.Note, title)
        items = items + note
        scope.launch { repository.save(note) }
    }

    /** Swaps the captured card [id] for what its parse produced: the card, and a Project's sub-item cards. */
    fun land(id: String, parsed: ParsedItem) {
        val landed = parsed.toItems(id, newId)
        val at = items.indexOfFirst { it.id == id }
        if (at < 0) return
        items = items.take(at) + landed + items.drop(at + 1)
        scope.launch { landed.forEach { repository.save(it) } }
    }

    /** Completing a Project's last active sub-item completes the Project too. */
    fun complete(item: Item) {
        val project = completedProject(item)
        finish(item, ItemStatus.Completed, dismissed = false, project)
        onCompleted(item)
        if (project != null) onCompleted(project)
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
    }
}
