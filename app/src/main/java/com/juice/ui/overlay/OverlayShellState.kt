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
import com.juice.core.intake.ChipSpan
import com.juice.core.intake.ItemType
import com.juice.core.intake.ParsedItem
import com.juice.core.intake.RulesFallback
import com.juice.core.intake.rewriteRelativeDeadline
import com.juice.core.intake.withDeadlineDay
import com.juice.core.intake.withInterval
import com.juice.core.intake.withIntervalDays
import com.juice.core.intake.withWeekday
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.core.routine.Cadence
import com.juice.core.character.CharacterRegistry
import com.juice.data.CharacterRepository
import com.juice.data.CREATOR_CARD_ID
import com.juice.data.CoachCardKind
import com.juice.data.CreateResult
import com.juice.data.ItemRepository
import com.juice.data.PendingCoachCardStore
import com.juice.data.advance
import com.juice.data.asRoutine
import com.juice.data.toItems
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The last ✓/✗, kept for [OverlayShellState.UNDO_WINDOW_MILLIS] so it can be taken back. */
data class Undo(val item: Item, val dismissed: Boolean)

/** The title being edited: which card, and the text typed so far. */
data class TitleEdit(val itemId: String, val text: String)

/** The chip whose picker is open: which card, and the [span] of its title the pick will replace. */
data class ChipPicker(val itemId: String, val span: ChipSpan)

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
    /** The day the overlay opened; Routines due later stay out of the carousel. */
    private val today: LocalDate = LocalDate.now(),
    /** Where coach cards are told apart and a dismissed meet card turns its character away; null when there are no characters. */
    private val characters: CharacterRepository? = null,
    /** Fires when a coach card is completed; the chat opens from here in a later ticket. */
    private val onCoachCardCompleted: (characterId: String, cardKind: CoachCardKind) -> Unit = { _, _ -> },
    /** Opens a chat with the character when its card is completed; [onFailed] is for the chat to call when the card should stay. */
    private val coachChatFor: (characterId: String, onFailed: () -> Unit, onConfirmed: () -> Unit) -> CoachChatState? = { _, _, _ -> null },
    /** Marks a coach card while its chat is open, so [restorePending] can bring it back after the process dies. */
    private val pendingCards: PendingCoachCardStore? = null,
    /** Every character, built-in and created, for finding the one a coach card belongs to. */
    private val registry: CharacterRegistry = CharacterRegistry { emptyList() },
    /** Where the character is generated, which calls the network. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** True for the Routines view's list: every Active Routine, finished for good by ✓/✗ instead of moving on. */
    private val listsRoutines: Boolean = false,
    /** The time a title is read against: "tomorrow" in an edited title, "next week" in a landed one. */
    private val clock: () -> Instant = Instant::now,
) {
    var items by mutableStateOf(initial)
        private set
    var undo by mutableStateOf<Undo?>(null)
        private set

    /** The open title editor, if any; held here so a tap outside the card can save it. */
    var titleEdit by mutableStateOf<TitleEdit?>(null)
        private set

    fun beginTitleEdit(item: Item) {
        titleEdit = TitleEdit(item.id, item.title)
    }

    fun changeTitleEdit(text: String) {
        titleEdit = titleEdit?.copy(text = text)
    }

    fun endTitleEdit() {
        titleEdit = null
    }

    /** Saves the open title edit and ends it; false when no title is being edited. */
    fun commitTitleEdit(): Boolean {
        val edit = titleEdit ?: return false
        items.firstOrNull { it.id == edit.itemId }?.let { setTitle(it, edit.text) }
        titleEdit = null
        return true
    }

    /** The open chip picker, if any. */
    var chipPicker by mutableStateOf<ChipPicker?>(null)
        private set

    fun openChipPicker(item: Item, span: ChipSpan) {
        chipPicker = ChipPicker(item.id, span)
    }

    fun dismissChipPicker() {
        chipPicker = null
    }

    /** The id of the Routine whose Repeat picker is open, if any. */
    var repeatPickerItemId by mutableStateOf<String?>(null)
        private set

    fun openRepeatPicker(item: Item) {
        repeatPickerItemId = item.id
    }

    fun dismissRepeatPicker() {
        repeatPickerItemId = null
    }

    /** True while a card's title is being edited; the shell hides the capture field meanwhile. */
    val editingTitle: Boolean get() = titleEdit != null

    /** The coach chat shown over the carousel, if one is open. */
    var coachChat by mutableStateOf<CoachChatState?>(null)
        private set

    /** The creator flow shown over the carousel, if the creator card is open. */
    var creator by mutableStateOf<CreatorState?>(null)
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

    /** The Routine card that just left the carousel because it is not due today, while it drains into the Routines button. */
    var draining by mutableStateOf<Item?>(null)
        private set

    fun drainDone() {
        draining = null
    }

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

    /** The Routines view's list, over the same repository; the carousel's own list is not touched by it. */
    val routines: OverlayShellState by lazy {
        OverlayShellState(
            repository, scope, onCompleted = onCompleted, onCompletionUndone = onCompletionUndone,
            today = today, io = io, listsRoutines = true,
        )
    }

    private var observingRoutines = false

    /** Keeps the cards in step with the repository, so a parse landing later updates an open card. */
    fun observe() {
        // The Routines view calls this each time it opens; one collector is enough.
        if (listsRoutines) {
            if (observingRoutines) return
            observingRoutines = true
        }
        scope.launch {
            (if (listsRoutines) repository.observeRoutines() else repository.observeActive(today)).collect { saved ->
                val flowOpen = creator != null
                val byId = saved.filterNot { flowOpen && it.id == CREATOR_CARD_ID }.associateBy { it.id }
                val landed = parsing.filter { id ->
                    val before = items.firstOrNull { it.id == id }
                    val after = byId[id]
                    after != null && (after.type != before?.type || after.deadline != before.deadline)
                }
                if (landed.isNotEmpty()) parsing = parsing - landed.toSet()
                // A Routine that left only because it is not due today drains into the Routines button; finish() has already taken its card out of items.
                if (!listsRoutines) {
                    items.firstOrNull { it.id !in byId && it.type == ItemType.Routine && it.cadence != null && it.nextDueOn?.isAfter(today) == true }
                        ?.let { draining = it }
                }
                val known = items.mapNotNull { byId[it.id] }
                val added = byId.values.filter { s -> items.none { it.id == s.id } }
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
    fun setType(item: Item, type: ItemType) = edit(item.id, IntakeField.Type) {
        when {
            // A List has no deadline or cadence.
            type == ItemType.List -> it.copy(type = type, cadence = null, nextDueOn = null, deadline = null)
            // Leaving Routine ends the recurrence.
            type != ItemType.Routine -> it.copy(type = type, cadence = null, nextDueOn = null)
            // A new Routine recurs daily from today unless it already has a cadence.
            it.cadence == null -> it.copy(type = type, cadence = Cadence(DayOfWeek.values().toSet()), nextDueOn = today)
            else -> it.copy(type = type)
        }
    }

    /** The steps of [project] still to do. */
    fun stepsOf(project: Item): List<Step> = project.steps.open()

    /** A Project completes through its steps, so ticking it while steps are open does nothing. */
    fun canComplete(item: Item): Boolean = !item.type.hasSteps || stepsOf(item).isEmpty()

    private fun now() = clock().atZone(ZoneId.systemDefault())

    /** The user rewrites a title; it is saved as typed, the card is sorted again by what it now says, and a late parse never changes it. */
    fun setTitle(item: Item, title: String) {
        val edited = title.trim()
        if (edited.isEmpty() || edited == item.title) return
        val parsed = RulesFallback.classify(edited, now())
        edit(item.id, IntakeField.Title) {
            val retitled = it.copy(title = edited, type = parsed.type, deadline = parsed.deadline, cadence = null, nextDueOn = null)
            parsed.cadence?.takeIf { parsed.type == ItemType.Routine }?.let { cadence -> retitled.asRoutine(cadence, today) } ?: retitled
        }
    }

    /** The user picks a new deadline [day] on the deadline chip [span]; the title says it and a late parse will not change it. */
    fun setDeadlineDay(item: Item, span: ChipSpan, day: LocalDate) =
        rewriteChip(item, IntakeField.Deadline) { withDeadlineDay(it, span, day, now()) }

    /** The user picks the interval [weeks] on the interval chip [span]. */
    fun setCadenceInterval(item: Item, span: ChipSpan, weeks: Int) =
        rewriteChip(item, IntakeField.Cadence) { withInterval(it, span, weeks) }

    /** The user picks the interval [days] on the interval chip [span]. */
    fun setCadenceIntervalDays(item: Item, span: ChipSpan, days: Int) =
        rewriteChip(item, IntakeField.Cadence) { withIntervalDays(it, span, days) }

    /** The user picks a [cadence] for a Routine from its Repeat picker; the title is untouched and a late parse will not undo it. */
    fun setCadence(item: Item, cadence: Cadence) {
        repeatPickerItemId = null
        edit(item.id, IntakeField.Cadence) { it.asRoutine(cadence, today) }
    }

    /** The user picks the weekday [to] on the weekday chip [span]. */
    fun setCadenceWeekday(item: Item, span: ChipSpan, to: DayOfWeek) =
        rewriteChip(item, IntakeField.Cadence) { withWeekday(it, span, to) }

    /** Rewrites the title of [item] and sorts it again by what it now says; marks [field], not Title, as the user's. */
    private fun rewriteChip(item: Item, field: IntakeField, rewrite: (String) -> String) {
        chipPicker = null
        val current = items.firstOrNull { it.id == item.id } ?: return
        val title = rewrite(current.title)
        val parsed = RulesFallback.classify(title, now())
        edit(item.id, field) {
            val retitled = it.copy(title = title, type = parsed.type, deadline = parsed.deadline, cadence = null, nextDueOn = null)
            parsed.cadence?.takeIf { parsed.type == ItemType.Routine }?.let { cadence -> retitled.asRoutine(cadence, today) } ?: retitled
        }
    }

    /** Applies a user edit on screen and saves the whole card in one write, so quick edits land in order. */
    private fun edit(id: String, field: IntakeField, change: (Item) -> Item) {
        val edited = items.firstOrNull { it.id == id }?.let { change(it).copy(editedFields = it.editedFields + field) } ?: return
        items = items.map { if (it.id == id) edited else it }
        scope.launch { repository.save(edited) }
    }

    /** Swaps the captured card [id] for what its parse produced: the card, with a Project's sub-items as steps. */
    fun land(id: String, parsed: ParsedItem) {
        val at = items.indexOfFirst { it.id == id }
        if (at < 0) return
        val old = items[at]
        val built = parsed.toItems(id, newId).first()
        val landed = when {
            IntakeField.Title in old.editedFields -> built.copy(title = old.title)
            built.type == ItemType.Goal -> built.copy(title = rewriteRelativeDeadline(built.title, now()))
            else -> built
        }
        items = items.take(at) + landed + items.drop(at + 1)
        scope.launch { repository.save(landed) }
    }

    /** Pins [item] after the cards starred before it and before the rest; one already starred keeps its place. */
    fun star(item: Item) {
        val current = items.firstOrNull { it.id == item.id } ?: return
        if (current.starredAt != null) return
        val starred = current.copy(starredAt = Instant.now())
        items = (items.map { if (it.id == item.id) starred else it })
            .sortedWith(compareBy({ it.starredAt == null }, { it.starredAt }))
        scope.launch { repository.star(item.id) }
    }

    /** Completing a Project's card. False when [canComplete] is not met. */
    fun complete(item: Item): Boolean {
        if (!canComplete(item)) return false
        if (item.id == CREATOR_CARD_ID) {
            items = items.filterNot { it.id == item.id }
            creator = CreatorState()
            return true
        }
        finish(item, ItemStatus.Completed, dismissed = false)
        onCompleted(item)
        coachCard(item)?.let { (id, kind) ->
            onCoachCardCompleted(id, kind)
            pendingCards?.add(item.id)
            coachChat = coachChatFor(
                id,
                { pendingCards?.remove(item.id); putBack(item, completed = true) },
                { pendingCards?.remove(item.id) },
            )?.also { it.start() } ?: run { pendingCards?.remove(item.id); null }
        }
        return true
    }

    /** Generates a coach for [hobby]; its meet card joins the carousel and the creator card is used up. On failure the error shows and nothing is saved. */
    fun createCharacter(hobby: String) {
        val flow = creator ?: return
        val repo = characters ?: return
        if (flow.busy || hobby.isBlank()) return
        flow.busy = true
        flow.error = null
        scope.launch {
            val result = withContext(io) { repo.createCharacter(hobby.trim()) }
            flow.busy = false
            when (result) {
                is CreateResult.Created -> {
                    repo.consumeCreatorCard()
                    repo.meetCard(result.character.id)?.let { card -> if (items.none { it.id == card.id }) items = items + card }
                    creator = null
                }
                is CreateResult.Failed -> flow.error = result.message
                CreateResult.LimitReached -> flow.error = "You have as many coaches as you can keep"
            }
        }
    }

    /** Closes the creator; the creator card comes back to the carousel. */
    fun cancelCreator() {
        if (creator == null) return
        creator = null
        scope.launch { repository.get(CREATOR_CARD_ID)?.let { putBack(it, completed = false) } }
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

    private fun coachCard(item: Item): Pair<String, CoachCardKind>? {
        val id = item.characterId ?: return null
        return characters?.cardKind(item)?.let { id to it }
    }

    /** A dismissed Routine skips this occurrence, a coach meet card dismisses its character; any other item is archived. */
    fun dismiss(item: Item) {
        val coach = coachCard(item)
        if (coach?.second != CoachCardKind.Meet) return finish(item, ItemStatus.Archived, dismissed = true)
        val character = registry.byId(coach.first)
        if (character == null) return finish(item, ItemStatus.Archived, dismissed = true)
        items = items.filterNot { it.id == item.id }
        scope.launch { characters?.dismiss(character) }
    }

    /** Brings back every coach card whose chat was open when the overlay last stopped, and clears the markers. */
    fun restorePending() {
        val ids = pendingCards?.ids().orEmpty()
        if (ids.isEmpty()) return
        scope.launch {
            ids.forEach { id ->
                repository.get(id)?.let { repository.save(it.copy(status = ItemStatus.Active)) }
                pendingCards?.remove(id)
            }
        }
    }

    /** Puts the undone item back where it was in the carousel. */
    fun undoLast() {
        val pending = undo ?: return
        putBack(pending.item, completed = !pending.dismissed)
    }

    /** Returns [item] to the carousel and the repository, taking back the Juice it earned if it was [completed]. */
    private fun putBack(item: Item, completed: Boolean) {
        if (undo?.item?.id == item.id) undo = null
        if (completed) onCompletionUndone()
        items = (items.filterNot { it.id == item.id } + item).sortedBy { order[it.id] ?: Int.MAX_VALUE }
        // Undispatched, so it queues for the write lock right after the save it undoes.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(item) }
    }

    fun expireUndo(expired: Undo) {
        if (undo == expired) undo = null
    }

    private val order = HashMap<String, Int>()

    private fun finish(item: Item, status: ItemStatus, dismissed: Boolean, restore: Item = item) {
        items.forEachIndexed { index, it -> order[it.id] = index }
        items = items.filterNot { it.id == item.id }
        undo = Undo(restore, dismissed)
        // A Routine moves on to its next due day and stays Active instead of finishing, except in the Routines view.
        val saved = if (!listsRoutines && item.type == ItemType.Routine && item.cadence != null) item.advance(today) else item.copy(status = status)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(saved) }
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

/** The creator card opened: [busy] while a coach is being made, [error] when that failed. */
class CreatorState {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}
