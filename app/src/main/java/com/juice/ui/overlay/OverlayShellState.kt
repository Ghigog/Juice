package com.juice.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The last ✓/✗, kept for [OverlayShellState.UNDO_WINDOW_MILLIS] so it can be taken back. */
data class Undo(val item: Item, val dismissed: Boolean)

/** State of the overlay: the Active items shown in the carousel and the pending undo. */
class OverlayShellState(
    private val repository: ItemRepository,
    private val scope: CoroutineScope,
    initial: List<Item> = emptyList(),
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

    fun complete(item: Item) = finish(item, ItemStatus.Completed, dismissed = false)

    fun dismiss(item: Item) = finish(item, ItemStatus.Archived, dismissed = true)

    /** Puts the undone item back where it was in the carousel. */
    fun undoLast() {
        val pending = undo ?: return
        undo = null
        items = (items + pending.item).sortedBy { order[it.id] ?: Int.MAX_VALUE }
        scope.launch { repository.save(pending.item) }
    }

    fun expireUndo(expired: Undo) {
        if (undo == expired) undo = null
    }

    private val order = HashMap<String, Int>()

    private fun finish(item: Item, status: ItemStatus, dismissed: Boolean) {
        items.forEachIndexed { index, it -> order[it.id] = index }
        items = items.filterNot { it.id == item.id }
        undo = Undo(item, dismissed)
        scope.launch { repository.save(item.copy(status = status)) }
    }

    companion object {
        const val UNDO_WINDOW_MILLIS = 10_000L
    }
}
