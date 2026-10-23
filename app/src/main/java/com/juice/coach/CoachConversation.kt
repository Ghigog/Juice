package com.juice.coach

import com.juice.core.character.Character
import com.juice.core.character.CoachProposal
import com.juice.core.character.CoachProposalResult
import com.juice.core.character.CoachTask
import com.juice.core.character.Expression
import com.juice.core.model.Item
import java.io.IOException

sealed interface CoachReply {
    data class Reply(val text: String, val expression: Expression = Expression.NEUTRAL) : CoachReply
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
 * One conversation with [character]. Earlier history and the player's current [coachTasks] go into the system
 * prompt, so a check-in builds on what was agreed. The conversation is saved to [store] after each successful turn.
 */
class CoachConversation(
    private val character: Character,
    private val client: CoachClient?,
    private val store: ConversationStore,
    private val onDeviceOnly: () -> Boolean,
    private val coachTasks: () -> List<Item>,
) {
    private val earlier: List<ChatMessage> = store.load(character.id)
    private val messages = mutableListOf<ChatMessage>()

    /** The expression of the coach's last message in the earlier history, neutral if there is none. */
    val lastExpression: Expression = earlier.lastOrNull { it.role == "assistant" }?.expression ?: Expression.NEUTRAL

    val cloudRequired: Boolean get() = client == null || onDeviceOnly()

    fun send(userText: String): CoachReply {
        if (client == null || onDeviceOnly()) return CoachReply.CloudRequired
        val turn = messages + ChatMessage("user", userText)
        return try {
            val (expression, text) = Expression.parse(client.chat(systemPrompt(), turn))
            messages += turn.last()
            messages += ChatMessage("assistant", text, expression)
            store.save(character.id, messages)
            CoachReply.Reply(text, expression)
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
        if (earlier.isNotEmpty()) {
            append("\nThis is a check-in. Your previous conversation with the player, for context:\n")
            earlier.forEach { append("${if (it.role == "user") "Player" else "You"}: ${it.content}\n") }
        }
    }.trimEnd()
}
