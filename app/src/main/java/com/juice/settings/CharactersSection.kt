package com.juice.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.character.Availability
import com.juice.core.character.Character
import com.juice.core.character.Expression
import com.juice.core.character.Monogram
import com.juice.core.memory.Memory
import com.juice.data.CharacterRepository
import com.juice.ui.face.CharacterFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TAG_CHARACTER_RESTORE_PREFIX = "character_restore_"
const val TAG_CHARACTER_DISABLE_PREFIX = "character_disable_"
const val TAG_CHARACTER_CONFIRM_DISABLE_PREFIX = "character_confirm_disable_"
const val TAG_CHARACTER_ENABLE_PREFIX = "character_enable_"
const val TAG_CHARACTER_RESET_PREFIX = "character_reset_"
const val TAG_CHARACTER_CONFIRM_RESET_PREFIX = "character_confirm_reset_"
const val TAG_CHARACTER_DELETE_PREFIX = "character_delete_"
const val TAG_CHARACTER_CONFIRM_DELETE_PREFIX = "character_confirm_delete_"
const val TAG_MEMORY_EMPTY = "memory_empty"
const val TAG_MEMORY_CLEAR = "memory_clear"

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

/** A character's picture: its face for a built-in, its monogram for a created one. */
@Composable
fun CharacterAvatar(character: Character, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
        if (character.hobby == null) {
            CharacterFace(character.face, Expression.NEUTRAL, Modifier.size(32.dp))
        } else {
            val monogram = Monogram(character)
            Box(Modifier.size(40.dp).background(Color.hsv(monogram.hue.toFloat(), 0.5f, 0.6f)), contentAlignment = Alignment.Center) {
                Text(monogram.initials, color = Color.White)
            }
        }
    }
}

/** The Characters section: Restore for a dismissed character, Disable and Reset (each after a confirmation) for an active one, Enable and Reset for a disabled one, Delete (after a confirmation) for a created one, only the state otherwise. */
@Composable
fun CharactersSection(settings: CharacterSettings, modifier: Modifier = Modifier) {
    // Cards can be dismissed in the carousel while this is closed, so read afresh each time it opens.
    LaunchedEffect(settings) { settings.refresh() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_characters))
        settings.rows.forEach { CharacterRowView(it, settings) }
        MemoryView(settings)
    }
}

@Composable
private fun MemoryView(settings: CharacterSettings) {
    val memory = settings.memory
    val noteLists = buildList {
        if (memory.shared.isNotEmpty()) add(stringResource(R.string.settings_memory_shared) to memory.shared)
        memory.characters.forEach { (id, notes) ->
            if (notes.isNotEmpty()) add(settings.nameOf(id) to notes)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_memory))
        if (noteLists.isEmpty()) {
            Text(stringResource(R.string.settings_memory_empty), fontSize = 12.sp, modifier = Modifier.testTag(TAG_MEMORY_EMPTY))
        } else {
            noteLists.forEach { (title, notes) ->
                Text(title, fontSize = 12.sp)
                notes.forEach { Text("• $it", fontSize = 12.sp) }
            }
            Button(onClick = { settings.clearMemory() }, modifier = Modifier.testTag(TAG_MEMORY_CLEAR)) {
                Text(stringResource(R.string.settings_memory_clear))
            }
        }
    }
}

@Composable
private fun CharacterRowView(row: CharacterRow, settings: CharacterSettings) {
    val id = row.character.id
    var confirming by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val created = row.character.hobby != null
    val canReset = row.status == CharacterStatus.Active || row.status == CharacterStatus.Disabled
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            CharacterAvatar(row.character)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(row.character.name)
                Text(
                    when (val status = row.status) {
                        is CharacterStatus.Locked -> stringResource(R.string.settings_character_locked, status.unlockLevel)
                        CharacterStatus.Dismissed -> stringResource(R.string.settings_character_dismissed)
                        CharacterStatus.Active -> stringResource(R.string.settings_character_active)
                        CharacterStatus.Disabled -> stringResource(R.string.settings_character_disabled)
                    },
                    fontSize = 12.sp,
                )
            }
            when (row.status) {
                CharacterStatus.Dismissed ->
                    Button(onClick = { settings.restore(row.character) }, modifier = Modifier.testTag(TAG_CHARACTER_RESTORE_PREFIX + id)) {
                        Text(stringResource(R.string.settings_character_restore))
                    }
                CharacterStatus.Disabled ->
                    Button(onClick = { settings.enable(row.character) }, modifier = Modifier.testTag(TAG_CHARACTER_ENABLE_PREFIX + id)) {
                        Text(stringResource(R.string.settings_character_enable))
                    }
                else -> Unit
            }
        }
        if ((canReset || created) && !confirming && !confirmingReset && !confirmingDelete) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.status == CharacterStatus.Active) {
                    Button(onClick = { confirming = true }, modifier = Modifier.testTag(TAG_CHARACTER_DISABLE_PREFIX + id)) {
                        Text(stringResource(R.string.settings_character_disable))
                    }
                }
                if (canReset) {
                    Button(onClick = { confirmingReset = true }, modifier = Modifier.testTag(TAG_CHARACTER_RESET_PREFIX + id)) {
                        Text(stringResource(R.string.settings_character_reset))
                    }
                }
                if (created) {
                    Button(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(TAG_CHARACTER_DELETE_PREFIX + id)) {
                        Text(stringResource(R.string.settings_character_delete))
                    }
                }
            }
        }
        if (confirmingReset && canReset) {
            Text(stringResource(R.string.settings_character_reset_confirm, row.character.name), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { confirmingReset = false; settings.reset(row.character) },
                    modifier = Modifier.testTag(TAG_CHARACTER_CONFIRM_RESET_PREFIX + id),
                ) { Text(stringResource(R.string.settings_character_reset)) }
                TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.settings_character_cancel)) }
            }
        }
        if (confirmingDelete && created) {
            Text(stringResource(R.string.settings_character_delete_confirm, row.character.name), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { confirmingDelete = false; settings.delete(row.character) },
                    modifier = Modifier.testTag(TAG_CHARACTER_CONFIRM_DELETE_PREFIX + id),
                ) { Text(stringResource(R.string.settings_character_delete)) }
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.settings_character_cancel)) }
            }
        }
        if (confirming && row.status == CharacterStatus.Active) {
            Text(stringResource(R.string.settings_character_disable_confirm, row.character.name), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { confirming = false; settings.disable(row.character) },
                    modifier = Modifier.testTag(TAG_CHARACTER_CONFIRM_DISABLE_PREFIX + id),
                ) { Text(stringResource(R.string.settings_character_disable)) }
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.settings_character_cancel)) }
            }
        }
    }
}
