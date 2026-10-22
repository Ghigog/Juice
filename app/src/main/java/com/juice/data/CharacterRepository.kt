package com.juice.data

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

/** A character's saved state together with the cards that belong to it. */
class CharacterRepository(private val store: CharacterStore, private val items: ItemRepository) {
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

    /** Turns [character] off for good: its cards and regular tasks are deleted and none are created again. */
    suspend fun disable(character: Character) {
        store.save(character.id, CharacterTransitions.disable(state(character.id)))
        items.deleteByCharacter(character.id)
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

    /** Adds the check-in card when one is due and not already there; returns whether it was added. */
    suspend fun createCheckInIfDue(character: Character, now: Instant = Instant.now()): Boolean {
        if (!CharacterTransitions.checkInDue(state(character.id), character, now)) return false
        if (items.get(checkInCardId(character)) != null) return false
        items.save(Item(checkInCardId(character), ItemType.Task, "Check in with ${character.name}", characterId = character.id))
        return true
    }

    private fun meetCard(character: Character) =
        Item(meetCardId(character), ItemType.Task, "Meet ${character.name}", characterId = character.id)

    private fun meetCardId(character: Character) = "meet-${character.id}"

    private fun checkInCardId(character: Character) = "checkin-${character.id}"
}
