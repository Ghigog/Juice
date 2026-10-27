package com.juice.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.coach.ChatMessage
import com.juice.core.character.CoachTask
import com.juice.core.character.Expression
import com.juice.core.character.face.FaceAnimator
import com.juice.ui.face.AnimatedCharacterFace
import com.juice.ui.face.FaceColors
import com.juice.ui.face.rememberFaceAnimator
import com.juice.ui.typing.TypewriterText
import kotlinx.coroutines.delay

const val TAG_COACH_CHAT = "coach_chat"
const val TAG_COACH_FACE = "coach_face"
const val TAG_COACH_INPUT = "coach_input"
const val TAG_COACH_SEND = "coach_send"
const val TAG_COACH_CONFIRM = "coach_confirm"
const val TAG_COACH_CANCEL = "coach_cancel"
const val TAG_COACH_HISTORY_BUTTON = "coach_history_button"
const val TAG_COACH_HISTORY = "coach_history"
const val TAG_COACH_LINE = "coach_line"

/** How long the player's own words stay in the box before the coach starts thinking. */
private const val YOU_SHOWN_MILLIS = 700L

private val FACE_SIZE = 168.dp

/** How far the face sinks into the box's top-right corner. */
private val FACE_OVERLAP = 44.dp

/**
 * The chat with a coach, as a visual novel: one dialogue box shows the latest line, the coach's face hovers over its
 * top-right corner and acts out each reply's expression, and History opens the whole conversation. The player's
 * reply shows in the same box, then the coach thinks, then answers over it. A proposal shows in the box with Confirm
 * and Cancel in place of the input. With [animate] false (reduce motion) the face holds still and nothing slides.
 */
@Composable
internal fun CoachChat(chat: CoachChatState, hud: Hud, modifier: Modifier = Modifier, animate: Boolean = true) {
    var historyOpen by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    LaunchedEffect(chat.busy) {
        thinking = false
        if (chat.busy) {
            delay(YOU_SHOWN_MILLIS)
            thinking = true
        }
    }
    val showingYou = chat.busy && !thinking
    val expression = if (chat.busy && thinking) Expression.THINKING else chat.expression
    val face = rememberFaceAnimator(chat.character, expression, animate)
    LaunchedEffect(showingYou) { if (showingYou) face.glance(-4f, 4f) }
    val shadow = FaceColors.of(chat.character)
    val proposal = chat.proposal

    Box(modifier.testTag(TAG_COACH_CHAT)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HistoryButton { historyOpen = true }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(top = FACE_SIZE - FACE_OVERLAP).fillMaxWidth()) {
                    DialogueBox(chat, hud, face, showingYou, thinking && chat.busy, Modifier.padding(top = 15.dp))
                    NameTag(
                        name = if (showingYou) stringResource(R.string.coach_you) else chat.character.name,
                        background = if (showingYou) Color.White else hud.accent,
                        animate = animate,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp),
                    )
                }
                val description = stringResource(R.string.coach_face_description, chat.character.name, expression.name.lowercase())
                AnimatedCharacterFace(
                    face, shadow,
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp).size(FACE_SIZE).testTag(TAG_COACH_FACE),
                    description,
                )
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
        AnimatedVisibility(
            visible = historyOpen,
            enter = if (animate) fadeIn(tween(160)) else EnterTransition.None,
            exit = if (animate) fadeOut(tween(160)) else ExitTransition.None,
        ) {
            History(chat, hud, animate) { historyOpen = false }
        }
    }
}

/** The latest line: the coach's newest reply (typing out if it is new), the player's words while sent, or thinking dots. */
@Composable
private fun DialogueBox(chat: CoachChatState, hud: Hud, face: FaceAnimator, showingYou: Boolean, thinking: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    val lineIndex = chat.messages.indexOfLast { it.role == "assistant" }
    val line = if (lineIndex >= 0) chat.messages[lineIndex].content else chat.character.openingLine
    val proposal = chat.proposal
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 170.dp, max = 340.dp)
            .clip(shape)
            .background(JuiceColors.white(0.14f))
            .border(1.dp, JuiceColors.white(0.28f), shape)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 22.dp)
            .testTag(TAG_COACH_LINE),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            thinking -> ThinkingDots(stringResource(R.string.coach_thinking))
            showingYou -> Text(
                chat.messages.lastOrNull { it.role == "user" }?.content.orEmpty(),
                fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = JuiceColors.white(0.82f),
            )
            else -> {
                if (lineIndex >= 0 && lineIndex == chat.typingIndex) {
                    TypewriterText(
                        line,
                        onLetter = { letter ->
                            face.letter(letter)
                            chat.feedback.mutter(chat.character.mutterPitch)
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        style = LocalTextStyle.current.copy(lineHeight = 26.sp, fontWeight = FontWeight.Medium),
                    )
                } else {
                    Text(line, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
                if (chat.proposalRejected) {
                    Text(stringResource(R.string.coach_proposal_rejected), fontSize = 15.sp, lineHeight = 21.sp, color = JuiceColors.white(0.82f))
                }
                if (proposal != null) Proposal(chat, proposal, hud)
            }
        }
    }
}

/** The speaker's name over the box's top-left edge; it pops in each time the speaker changes. */
@Composable
private fun NameTag(name: String, background: Color, animate: Boolean, modifier: Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(name) {
        if (animate) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = -2f }
            .height(30.dp)
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = JuiceColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ThinkingDots(description: String) {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        Modifier.height(26.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.offset(y = (-7 * lift).dp).size(9.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun HistoryButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(JuiceColors.white(0.14f))
            .border(1.dp, JuiceColors.white(0.25f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 18.dp)
            .testTag(TAG_COACH_HISTORY_BUTTON),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(18.dp)) {
            val u = size.minDimension / 24f
            val stroke = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(Color.White, radius = 8.5f * u, center = Offset(12f * u, 12f * u), style = stroke)
            drawPath(Path().apply { moveTo(12f * u, 7.5f * u); lineTo(12f * u, 12f * u); lineTo(15f * u, 14f * u) }, Color.White, style = stroke)
        }
        Text(stringResource(R.string.coach_history), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** The whole conversation in a sheet over the chat; a tap outside it or the close button closes it. */
@Composable
private fun History(chat: CoachChatState, hud: Hud, animate: Boolean, onClose: () -> Unit) {
    val shown = listOf(ChatMessage("assistant", chat.character.openingLine)) + chat.messages
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { list.scrollToItem(shown.lastIndex.coerceAtLeast(0)) }
    var raised by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { raised = true }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // The scrim is a sibling behind the sheet, not its parent, so its click doesn't merge the sheet into one node.
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        AnimatedVisibility(
            visible = raised,
            enter = if (animate) slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it } else EnterTransition.None,
            exit = if (animate) slideOutVertically { it } else ExitTransition.None,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(JuiceColors.Sheet)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
                    .testTag(TAG_COACH_HISTORY),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(JuiceColors.white(0.3f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.coach_history_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    val close = stringResource(R.string.coach_history_close)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(JuiceColors.white(0.14f))
                            .clickable(role = Role.Button, onClick = onClose)
                            .semantics { contentDescription = close },
                        contentAlignment = Alignment.Center,
                    ) { CrossIcon() }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown) { m -> HistoryBubble(m, chat.character.name, hud) }
                }
            }
        }
    }
}

@Composable
private fun HistoryBubble(message: ChatMessage, name: String, hud: Hud) {
    val mine = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) hud.accent else JuiceColors.white(0.14f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val color = if (mine) JuiceColors.Ink else Color.White
            Text(
                if (mine) stringResource(R.string.coach_you) else "$name · ${message.expression.name.lowercase()}",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color.copy(alpha = 0.75f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(message.content, fontSize = 15.sp, lineHeight = 21.sp, color = color)
        }
    }
}

@Composable
private fun CrossIcon() {
    Canvas(Modifier.size(16.dp)) {
        val width = 2.2.dp.toPx()
        drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), width, StrokeCap.Round)
        drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), width, StrokeCap.Round)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f).testTag(TAG_COACH_INPUT),
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
        val label = stringResource(R.string.coach_send)
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(hud.accent)
                .clickable(role = Role.Button) { send() }
                .semantics { contentDescription = label }
                .testTag(TAG_COACH_SEND),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val u = size.minDimension / 24f
                val stroke = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(
                    Path().apply { moveTo(12f * u, 19f * u); lineTo(12f * u, 5f * u); moveTo(5f * u, 12f * u); lineTo(12f * u, 5f * u); lineTo(19f * u, 12f * u) },
                    JuiceColors.Ink, style = stroke,
                )
            }
        }
    }
}

@Composable
private fun Proposal(chat: CoachChatState, tasks: List<CoachTask>, hud: Hud) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.28f))
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
            Text(line, fontSize = 15.sp, color = Color.White)
        }
    }
}
