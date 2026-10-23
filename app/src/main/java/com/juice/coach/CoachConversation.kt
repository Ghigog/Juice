package com.juice.coach

import com.juice.core.character.Character
import com.juice.core.character.CoachProposal
import com.juice.core.character.CoachProposalResult
import com.juice.core.character.CoachTask
import com.juice.core.character.Characters
import com.juice.core.memory.MemoryPrompt
import com.juice.core.memory.MemoryUpdate
import com.juice.core.memory.MemoryUpdateResult
import com.juice.core.model.Item
import com.juice.memory.MemoryStore
import java.io.IOException

sealed interface CoachReply {
    data class Reply(val text: String) : CoachReply
    /** On-device only is on, or there is no key: nothing was sent. */
    data object CloudRequired : CoachReply
    data class Error(val message: String) : CoachReply
}

sealed interface CoachFinish {
    data class Proposal(val tasks: List<CoachTask>) : CoachFinish
    data object CloudRequired : CoachFinish
    data class Error(val message: String) : CoachFinish
    /** The coach's proposal failed validation; no tasks are returned. */
    data class Rejected(val reason: String) : CoachFinish
}

/**
 * One conversation with [character]. What the characters remember (from [memoryStore]) and the player's current [coachTasks] go into the
 * system prompt, so a check-in builds on what was agreed. The conversation is saved to [store] after each successful
 * turn, and the notes are updated once a proposal is accepted.
 */
class CoachConversation(
    private val character: Character,
    private val client: CoachClient?,
    private val store: ConversationStore,
    private val memoryStore: MemoryStore,
    private val onDeviceOnly: () -> Boolean,
    private val coachTasks: () -> List<Item>,
) {
    private val messages = mutableListOf<ChatMessage>()

    val cloudRequired: Boolean get() = client == null || onDeviceOnly()

    fun send(userText: String): CoachReply {
        if (client == null || onDeviceOnly()) return CoachReply.CloudRequired
        val turn = messages + ChatMessage("user", userText)
        return try {
            val text = client.chat(systemPrompt(), turn)
            messages += turn.last()
            messages += ChatMessage("assistant", text)
            store.save(character.id, messages)
            CoachReply.Reply(text)
        } catch (e: IOException) {
            CoachReply.Error(e.message ?: "Coach unreachable")
        }
    }

    fun finish(): CoachFinish {
        if (client == null || onDeviceOnly()) return CoachFinish.CloudRequired
        val ask = ChatMessage("user", "Please propose the tasks we agreed as JSON only.")
        return try {
            val json = client.chat(systemPrompt(), messages + ask, jsonMode = true)
            when (val result = CoachProposal.validate(json, coachTasks().map { it.id }.toSet())) {
                is CoachProposalResult.Valid -> {
                    messages += ask
                    messages += ChatMessage("assistant", json)
                    store.save(character.id, messages)
                    updateMemory()
                    CoachFinish.Proposal(result.tasks)
                }
                is CoachProposalResult.Rejected -> CoachFinish.Rejected(result.reason)
            }
        } catch (e: IOException) {
            CoachFinish.Error(e.message ?: "Coach unreachable")
        }
    }

    private fun systemPrompt(): String = buildString {
        append(character.personaPrompt)
        val tasks = coachTasks()
        if (tasks.isNotEmpty()) {
            append("\n\nThe player's current coach tasks (use these ids to change or remove):\n")
            tasks.forEach { append("- [${it.id}] ${it.title}${it.cadence?.let { c -> " (${c.label()})" } ?: ""}\n") }
        }
        val memory = MemoryPrompt.promptSection(memoryStore.load(), character.id)
        if (memory.isNotEmpty()) append("\n").append(memory).append("\n")
    }.trimEnd()

    /** Best effort: a failed or invalid update leaves the stored notes as they were. */
    private fun updateMemory() {
        val current = memoryStore.load()
        val conversation = messages.joinToString("\n") { "${if (it.role == "user") "Player" else character.name}: ${it.content}" }
        try {
            val json = client!!.chat(
                "You keep short notes about the player for the characters.",
                listOf(ChatMessage("user", MemoryPrompt.updatePrompt(current, conversation))),
                jsonMode = true,
            )
            val result = MemoryUpdate.validate(json, Characters.all.map { it.id }.toSet())
            if (result is MemoryUpdateResult.Valid) memoryStore.save(result.memory)
        } catch (e: IOException) {
            // Notes stay unchanged.
        }
    }
}
