package com.juice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.ui.face.CharacterFace
import com.juice.ui.typing.TypewriterText
import com.juice.coach.ChatMessage
import com.juice.core.character.CoachTask

const val TAG_COACH_CHAT = "coach_chat"
const val TAG_COACH_FACE = "coach_face"
const val TAG_COACH_INPUT = "coach_input"
const val TAG_COACH_SEND = "coach_send"
const val TAG_COACH_FINISH = "coach_finish"
const val TAG_COACH_CONFIRM = "coach_confirm"
const val TAG_COACH_CANCEL = "coach_cancel"

/** The chat with a coach: a scrolling message list, the input with Send and Finish, then the proposal with Confirm and Cancel. */
@Composable
internal fun CoachChat(chat: CoachChatState, hud: Hud, modifier: Modifier = Modifier) {
    val opening = ChatMessage("assistant", chat.character.openingLine)
    val shown = listOf(opening) + chat.messages
    val list = rememberLazyListState()
    LaunchedEffect(shown.size, chat.proposal, chat.busy, chat.proposalRejected) {
        list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    }
    val proposal = chat.proposal
    Column(modifier.testTag(TAG_COACH_CHAT)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CharacterFace(chat.character.face, chat.expression, Modifier.size(56.dp).testTag(TAG_COACH_FACE))
            Text(chat.character.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(shown) { i, m ->
                val typed = i > 0 && i - 1 == chat.typingIndex
                Bubble(m, hud, if (typed) { letter -> chat.feedback.mutter(chat.character.mutterPitch) } else null)
            }
            if (proposal != null) item { Proposal(chat, proposal, hud) }
            if (chat.proposalRejected) item { Bubble(ChatMessage("assistant", stringResource(R.string.coach_proposal_rejected)), hud) }
            if (chat.busy) item { Bubble(ChatMessage("assistant", stringResource(R.string.coach_thinking)), hud) }
        }
        Spacer(Modifier.height(12.dp))
        if (proposal != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.coach_cancel), JuiceColors.white(0.14f), Color.White, Modifier.weight(1f).testTag(TAG_COACH_CANCEL)) { chat.cancel() }
                PillButton(stringResource(R.string.coach_confirm), hud.accent, JuiceColors.Ink, Modifier.weight(1f).testTag(TAG_COACH_CONFIRM)) { chat.confirm() }
            }
        } else {
            Input(chat, hud)
        }
    }
}

@Composable
private fun Input(chat: CoachChatState, hud: Hud) {
    var draft by remember { mutableStateOf("") }
    fun send() {
        if (draft.isBlank() || chat.busy) return
        chat.send(draft)
        draft = ""
    }
    val shape = RoundedCornerShape(28.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().testTag(TAG_COACH_INPUT),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(shape)
                        .background(JuiceColors.white(0.14f))
                        .border(1.dp, JuiceColors.white(0.25f), shape)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) Text(stringResource(R.string.coach_input_hint), fontSize = 15.sp, color = JuiceColors.white(0.6f))
                    field()
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.coach_send), JuiceColors.white(0.14f), Color.White, Modifier.weight(1f).testTag(TAG_COACH_SEND)) { send() }
            PillButton(stringResource(R.string.coach_finish), hud.accent, JuiceColors.Ink, Modifier.weight(1f).testTag(TAG_COACH_FINISH)) { chat.finish() }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, hud: Hud, onLetter: ((Char) -> Unit)? = null) {
    val mine = message.role == "user"
    val color = if (mine) JuiceColors.Ink else Color.White
    val look = Modifier
        .widthIn(max = 300.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(if (mine) hud.accent else JuiceColors.white(0.14f))
        .padding(horizontal = 14.dp, vertical = 10.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (onLetter != null) TypewriterText(message.content, onLetter, look, color = color, fontSize = 15.sp)
        else Text(message.content, fontSize = 15.sp, color = color, modifier = look)
    }
}

@Composable
private fun Proposal(chat: CoachChatState, tasks: List<CoachTask>, hud: Hud) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JuiceColors.white(0.1f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.coach_proposal_title), fontWeight = FontWeight.Bold, color = hud.accent)
        tasks.forEach { task ->
            val line = when (task) {
                is CoachTask.Add -> stringResource(R.string.coach_proposal_add, task.title, task.cadence.label())
                is CoachTask.Change -> stringResource(R.string.coach_proposal_change, task.title, task.cadence.label())
                is CoachTask.Remove -> stringResource(R.string.coach_proposal_remove, chat.titles[task.id] ?: task.id)
            }
            Text(line, fontSize = 15.sp)
        }
    }
}
