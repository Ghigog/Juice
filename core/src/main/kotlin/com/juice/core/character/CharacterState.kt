package com.juice.core.character

import java.time.Duration
import java.time.Instant

enum class Availability { Locked, Unlocked, MetPending }

/** Where the player stands with one character. [MetPending] is an unlocked character whose meet card is not yet answered. */
data class CharacterState(
    val availability: Availability = Availability.Locked,
    val dismissed: Boolean = false,
    val disabled: Boolean = false,
    val lastConversationAt: Instant? = null,
)

object CharacterTransitions {
    /** Unlocks a locked, not dismissed or disabled character once [level] reaches its unlock level, with a pending meet card. */
    fun onLevel(state: CharacterState, character: Character, level: Int): CharacterState =
        if (state.availability == Availability.Locked && !state.dismissed && !state.disabled && level >= character.unlockLevel) {
            state.copy(availability = Availability.MetPending)
        } else {
            state
        }

    fun dismiss(state: CharacterState): CharacterState = state.copy(dismissed = true)

    /** Brings a dismissed character back as a pending meet card, if it had been unlocked. */
    fun restore(state: CharacterState): CharacterState =
        if (!state.dismissed) state else state.copy(dismissed = false)

    fun disable(state: CharacterState): CharacterState = state.copy(disabled = true)

    fun conversationFinished(state: CharacterState, at: Instant): CharacterState =
        state.copy(availability = Availability.Unlocked, lastConversationAt = at)

    /** True once [Character.checkInIntervalDays] have passed since the last conversation; never when dismissed or disabled. */
    fun checkInDue(state: CharacterState, character: Character, now: Instant): Boolean {
        val last = state.lastConversationAt ?: return false
        if (state.dismissed || state.disabled || state.availability == Availability.Locked) return false
        return Duration.between(last, now) >= Duration.ofDays(character.checkInIntervalDays.toLong())
    }
}
