package com.juice.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juice.coach.ChatMessage
import com.juice.coach.CoachConversation
import com.juice.coach.CoachFinish
import com.juice.coach.CoachReply
import com.juice.core.character.Character
import com.juice.core.character.CoachTask
import com.juice.core.model.Item
import com.juice.data.CharacterRepository
import com.juice.feedback.Feedback
import com.juice.feedback.FeedbackEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a chat closed on its own: the coach needs cloud access, or could not be reached. */
enum class CoachNotice { CloudRequired, Unreachable }

/**
 * An open chat with [character]. Messages go through [conversation] off the main thread; Finish asks for the
 * proposal, which Confirm applies through [characters] and Cancel drops. When the coach needs the cloud or fails,
 * the chat closes with a [notice] and calls [onFailed] so the coach card the player completed can stay. Cancel
 * calls [onFailed] too, without a notice.
 */
class CoachChatState(
    val character: Character,
    private val conversation: CoachConversation,
    private val characters: CharacterRepository,
    private val scope: CoroutineScope,
    /** The player's current coach tasks, for naming the ones a proposal changes or removes. */
    private val coachTasks: () -> List<Item> = { emptyList() },
    private val onFailed: () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Plays the coach's mutter as a reply types out. */
    val feedback: Feedback = SilentFeedback,
) {
    var messages by mutableStateOf(emptyList<ChatMessage>())
        private set

    /** The coach's expression: the saved conversation's last one at first, then each reply's; it stays put across proposals and errors. */
    var expression by mutableStateOf(conversation.lastExpression)
        private set

    /** Index in [messages] of the newest assistant reply, the only one that types out; -1 before any. */
    var typingIndex by mutableStateOf(-1)
        private set
    var proposal by mutableStateOf<List<CoachTask>?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var open by mutableStateOf(true)
        private set
    var notice by mutableStateOf<CoachNotice?>(null)
        private set

    /** Titles of the tasks the proposal changes or removes, by id. */
    var titles by mutableStateOf(emptyMap<String, String>())
        private set

    /** True when the coach's last proposal could not be used and the player can ask again. */
    var proposalRejected by mutableStateOf(false)
        private set

    /** Closes the chat at once, without sending anything, if the coach cannot be reached from this device. */
    fun start() {
        scope.launch {
            if (withContext(io) { conversation.cloudRequired }) fail(CoachNotice.CloudRequired)
        }
    }

    fun send(text: String) {
        val said = text.trim()
        if (said.isEmpty() || busy || !open) return
        busy = true
        proposalRejected = false
        messages = messages + ChatMessage("user", said)
        scope.launch {
            when (val reply = withContext(io) { conversation.send(said) }) {
                is CoachReply.Reply -> {
                    expression = reply.expression
                    messages = messages + ChatMessage("assistant", reply.text, reply.expression)
                    typingIndex = messages.lastIndex
                }
                CoachReply.CloudRequired -> fail(CoachNotice.CloudRequired)
                is CoachReply.Error -> fail(CoachNotice.Unreachable)
            }
            busy = false
        }
    }

    /** Asks the coach for the proposal. */
    fun finish() {
        if (busy || !open || proposal != null) return
        busy = true
        proposalRejected = false
        scope.launch {
            val (result, current) = withContext(io) { conversation.finish() to coachTasks() }
            when (result) {
                is CoachFinish.Proposal -> {
                    titles = current.associate { it.id to it.title }
                    proposal = result.tasks
                }
                CoachFinish.CloudRequired -> fail(CoachNotice.CloudRequired)
                is CoachFinish.Error -> fail(CoachNotice.Unreachable)
                is CoachFinish.Rejected -> proposalRejected = true
            }
            busy = false
        }
    }

    /** Applies the proposal to the player's regular tasks and records the conversation time. */
    fun confirm() {
        val tasks = proposal ?: return
        open = false
        scope.launch {
            characters.createTasks(character.id, tasks)
            characters.recordConversation(character.id)
        }
    }

    /** Drops the proposal; nothing is created. The coach card comes back, with no notice. */
    fun cancel() {
        if (!open) return
        proposal = null
        open = false
        onFailed()
    }

    private fun fail(reason: CoachNotice) {
        if (!open) return
        notice = reason
        open = false
        onFailed()
    }
}

private object SilentFeedback : Feedback {
    override fun play(event: FeedbackEvent, boosted: Boolean) {}
}
