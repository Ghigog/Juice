package com.juice.overlay

import com.juice.core.character.CharacterRegistry
import com.juice.core.character.Characters
import com.juice.feedback.Feedback
import com.juice.feedback.FeedbackEvent
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope

class CoachChatsTest {
    private val quiet = object : Feedback {
        override fun play(event: FeedbackEvent, boosted: Boolean) {}
    }

    private fun chats(disabled: Set<String>) = CoachChats(
        registry = { CharacterRegistry { emptyList() } }, disabledIds = { disabled },
        characters = { error("not reached") }, conversationFor = { error("not reached") },
        coachTasks = { emptyList() }, scope = TestScope(),
    )

    @Test fun anUnknownCharacterGetsNoChat() = assertNull(chats(emptySet()).chatFor("nobody", {}, {}, quiet))

    @Test fun aDisabledCharacterGetsNoChat() {
        val id = Characters.all.first().id
        assertNull(chats(setOf(id)).chatFor(id, {}, {}, quiet))
    }
}
