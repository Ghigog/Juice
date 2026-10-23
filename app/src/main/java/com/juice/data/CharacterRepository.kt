package com.juice.data

import com.juice.coach.ConversationStore
import com.juice.core.character.Availability
import com.juice.core.character.Character
import com.juice.core.character.CharacterState
import com.juice.core.character.CharacterTransitions
import com.juice.core.character.CoachTask
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Which of a character's cards an item is. */
enum class CoachCardKind { Meet, CheckIn }

/** A character's saved state together with the cards that belong to it. */
class CharacterRepository(
    private val store: CharacterStore,
    private val items: ItemRepository,
    private val conversations: ConversationStore? = null,
) {
    fun state(characterId: String): CharacterState = store.load(characterId)

    /** Unlocks [character] once [level] is high enough and adds its meet card; asking again adds nothing. */
    suspend fun unlockAtLevel(character: Character, level: Int) {
        val before = state(character.id)
        val after = CharacterTransitions.onLevel(before, character, level)
        if (after == before) return
        store.save(character.id, after)
        items.save(meetCard(character))
    }

    /** The player turned the meet card away; it goes, and can come back with [restore]. */
    suspend fun dismiss(character: Character) {
        store.save(character.id, CharacterTransitions.dismiss(state(character.id)))
        items.delete(meetCardId(character))
    }

    /** Brings a dismissed, unlocked character back with a fresh meet card. */
    suspend fun restore(character: Character) {
        val before = state(character.id)
        if (!before.dismissed) return
        val restored = CharacterTransitions.restore(before)
        if (restored.availability == Availability.Locked) {
            store.save(character.id, restored)
            return
        }
        store.save(character.id, restored.copy(availability = Availability.MetPending))
        items.save(meetCard(character))
    }

    /** Hides [character]: its cards, tasks and chat go from the lists and no new card is made, but all it has stays stored. */
    suspend fun disable(character: Character) {
        store.save(character.id, CharacterTransitions.disable(state(character.id)))
    }

    /** Shows a disabled [character] again, with everything it had. */
    suspend fun enable(character: Character) {
        store.save(character.id, CharacterTransitions.enable(state(character.id)))
    }

    /** Starts [character] over: its items and saved conversation are deleted and it is enabled and new. Not for a locked one. */
    suspend fun reset(character: Character) {
        if (state(character.id).availability == Availability.Locked) return
        items.deleteByCharacter(character.id)
        conversations?.clear(character.id)
        store.save(character.id, CharacterState())
    }

    /** Applies a validated proposal: new Routines are added, changed ones updated, removed ones deleted. */
    suspend fun createTasks(
        characterId: String,
        tasks: List<CoachTask>,
        today: LocalDate = LocalDate.now(),
        newId: () -> String = { UUID.randomUUID().toString() },
    ) {
        tasks.forEach { task ->
            when (task) {
                is CoachTask.Add ->
                    items.save(Item(newId(), ItemType.Routine, task.title, characterId = characterId).asRoutine(task.cadence, today))
                is CoachTask.Change -> items.get(task.id)?.takeIf { it.characterId == characterId }?.let {
                    items.save(it.copy(title = task.title).asRoutine(task.cadence, today))
                }
                is CoachTask.Remove -> if (items.get(task.id)?.characterId == characterId) items.delete(task.id)
            }
        }
    }

    /** Notes that the player finished a conversation with [characterId] at [at]; the next check-in counts from here. */
    suspend fun recordConversation(characterId: String, at: Instant = Instant.now()) {
        store.save(characterId, CharacterTransitions.conversationFinished(state(characterId), at))
    }

    /** Adds the check-in card when one is due and not already there; returns whether it was added. */
    suspend fun createCheckInIfDue(character: Character, now: Instant = Instant.now()): Boolean {
        if (!CharacterTransitions.checkInDue(state(character.id), character, now)) return false
        if (items.get(checkInCardId(character)) != null) return false
        items.save(Item(checkInCardId(character), ItemType.Task, "Check in with ${character.name}", characterId = character.id))
        return true
    }

    /** The kind of coach card [item] is, or null for a normal card. */
    fun cardKind(item: Item): CoachCardKind? = when {
        item.characterId == null -> null
        item.id.startsWith("meet-") -> CoachCardKind.Meet
        item.id.startsWith("checkin-") -> CoachCardKind.CheckIn
        else -> null
    }

    private fun meetCard(character: Character) =
        Item(meetCardId(character), ItemType.Task, "Meet ${character.name}", characterId = character.id)

    private fun meetCardId(character: Character) = "meet-${character.id}"

    private fun checkInCardId(character: Character) = "checkin-${character.id}"
}
