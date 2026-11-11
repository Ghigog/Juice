package com.juice.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.core.character.Availability
import com.juice.core.character.Character
import com.juice.core.memory.Memory
import com.juice.data.CharacterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where the player stands with a character, as Settings shows it. A [Disabled] character is only hidden; Enable brings it back. */
sealed interface CharacterStatus {
    data class Locked(val unlockLevel: Int) : CharacterStatus
    data object Dismissed : CharacterStatus
    data object Active : CharacterStatus
    data object Disabled : CharacterStatus
}

data class CharacterRow(val character: Character, val status: CharacterStatus)

/** One [CharacterRow] per registered character, read from [characters]; [refresh] after anything that changes them. */
class CharacterSettings(
    private val characters: CharacterRepository,
    private val scope: CoroutineScope,
    private val level: () -> Int,
) {
    var rows by mutableStateOf<List<CharacterRow>>(emptyList())
        private set

    /** What the characters remember, as of the last [refresh] or [clearMemory]. */
    var memory by mutableStateOf(Memory())
        private set

    fun refresh() {
        memory = characters.memory()
        scope.launch { rows = read() }
    }

    fun clearMemory() {
        characters.clearMemory()
        memory = characters.memory()
    }

    fun restore(character: Character) {
        scope.launch {
            characters.restore(character)
            rows = read()
        }
    }

    fun disable(character: Character) {
        scope.launch {
            characters.disable(character)
            rows = read()
        }
    }

    fun enable(character: Character) {
        scope.launch {
            characters.enable(character)
            refresh()
        }
    }

    fun reset(character: Character) {
        scope.launch {
            characters.reset(character, level())
            refresh()
        }
    }

    /** Removes a created character and everything of it, then reads the rows again. */
    fun delete(character: Character) {
        scope.launch {
            characters.deleteCharacter(character.id)
            refresh()
        }
    }

    /** The name [id] shows under, or [id] itself when no character has it. */
    fun nameOf(id: String): String = characters.registry().byId(id)?.name ?: id

    private suspend fun read() = characters.registry().all().map { character ->
        val state = characters.state(character.id)
        CharacterRow(
            character,
            when {
                state.disabled -> CharacterStatus.Disabled
                state.dismissed || characters.canRestore(character) -> CharacterStatus.Dismissed
                state.availability == Availability.Locked -> CharacterStatus.Locked(character.unlockLevel)
                else -> CharacterStatus.Active
            },
        )
    }
}
