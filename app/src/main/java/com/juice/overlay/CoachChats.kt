package com.juice.overlay

import com.juice.core.character.Character
import com.juice.core.character.CharacterRegistry
import com.juice.data.CharacterRepository
import com.juice.core.model.Item
import com.juice.feedback.Feedback
import com.juice.ui.overlay.CoachChatState
import com.juice.coach.CoachConversation
import kotlinx.coroutines.CoroutineScope
import java.time.Clock

/** Makes the coach conversations the overlay opens; a disabled or unknown character gets none. */
class CoachChats(
    private val registry: () -> CharacterRegistry,
    private val disabledIds: () -> Set<String>,
    private val characters: () -> CharacterRepository,
    private val conversationFor: (Character) -> CoachConversation,
    private val coachTasks: suspend (characterId: String) -> List<Item>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun chatFor(characterId: String, onFailed: () -> Unit, onConfirmed: () -> Unit, feedback: Feedback): CoachChatState? =
        registry().byId(characterId)?.takeUnless { characterId in disabledIds() }?.let { character ->
            CoachChatState(
                character, conversationFor(character), characters(), scope,
                { coachTasks(characterId) }, onFailed, onConfirmed, feedback = feedback, clock = clock,
            )
        }
}
