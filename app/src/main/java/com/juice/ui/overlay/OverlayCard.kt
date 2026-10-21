package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.gesture.GesturePoint
import com.juice.core.gesture.GestureRecognizer
import com.juice.core.gesture.GestureResult
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.feedback.FeedbackEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Pause after the last stroke before the drawing is read, so an X can take two strokes. */
const val STROKE_PAUSE_MILLIS = 420L
const val FLY_MILLIS = 300

private enum class Tint { OK, X, BAD }

/**
 * The frosted centre card: the current item, its type pill, worth and the drawing surface. A ✓
 * completes, an ✗ (one or two strokes) dismisses; long-press the title to edit it.
 */
@Composable
internal fun CentreCard(
    state: OverlayShellState,
    item: Item?,
    hud: Hud,
    tapButtons: Boolean,
    reduceMotion: Boolean,
    onDeviceOnly: Boolean,
    actions: OverlayActions,
    effects: OverlayEffects,
    modifier: Modifier = Modifier,
) {
    val recognizer = remember { GestureRecognizer() }
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var tint by remember { mutableStateOf<Tint?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var editing by remember(item?.id) { mutableStateOf(false) }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    val shake = remember { Animatable(0f) }
    val flyX = remember(item?.id) { Animatable(0f) }
    val flyY = remember(item?.id) { Animatable(0f) }
    val flyAlpha = remember(item?.id) { Animatable(1f) }
    var busy by remember(item?.id) { mutableStateOf(false) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    val notRecognised = stringResource(R.string.overlay_not_recognised)
    val finishSteps = stringResource(R.string.overlay_finish_steps)
    val archived = stringResource(R.string.overlay_archived)

    fun say(text: String) {
        hint = text
        scope.launch { delay(1300); if (hint == text) hint = null }
    }

    fun reject(text: String) {
        actions.feedback(FeedbackEvent.NOT_RECOGNISED)
        say(text)
        if (!reduceMotion) scope.launch {
            shake.animateTo(0f, keyframes {
                durationMillis = 300
                -9f at 75; 8f at 150; -5f at 225
            })
        }
    }

    fun act(complete: Boolean) {
        val target = item ?: return
        if (busy) return
        if (complete && !state.canComplete(target)) return reject(finishSteps)
        busy = true
        scope.launch {
            if (!reduceMotion) {
                if (complete) {
                    launch { flyY.animateTo(-60f, tween(FLY_MILLIS)) }
                } else {
                    launch { flyX.animateTo(-70f, tween(FLY_MILLIS)) }
                }
                flyAlpha.animateTo(0f, tween(FLY_MILLIS))
            }
            if (complete) {
                state.complete(target)
            } else {
                actions.feedback(FeedbackEvent.DISMISS)
                state.dismiss(target)
                effects.float(archived)
            }
            busy = false
        }
    }

    fun read() {
        val drawn = strokes.toList()
        strokes.clear()
        val result = recognizer.recognise(drawn.flatten().map { GesturePoint(it.x.toDouble(), it.y.toDouble()) })
        tint = when (result) {
            is GestureResult.Checkmark -> Tint.OK
            is GestureResult.Dismiss -> Tint.X
            GestureResult.NotRecognised -> Tint.BAD
        }
        strokes.addAll(drawn)
        scope.launch {
            delay(380)
            strokes.clear()
            tint = null
        }
        when (result) {
            is GestureResult.Checkmark -> act(complete = true)
            is GestureResult.Dismiss -> act(complete = false)
            GestureResult.NotRecognised -> reject(notRecognised)
        }
    }

    val drawing = Modifier.pointerInput(item?.id, busy) {
        if (item == null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            if (busy) return@awaitEachGesture
            readJob?.cancel()
            if (tint != null) { strokes.clear(); tint = null }
            current.clear()
            current += down.position
            down.consume()
            // Holding still is a long press, which edits the title when it lands on it; moving starts a stroke.
            val longPress = viewConfiguration.longPressTimeoutMillis
            var stillLifted = false
            val outcome: Boolean? = withTimeoutOrNull(longPress) {
                var lifted: Boolean? = null
                while (lifted == null) {
                    val change = awaitPointerEvent().changes.first()
                    change.consume()
                    current += change.position
                    if (!change.pressed) {
                        // Lifted without moving, after the long-press time: still a long press.
                        stillLifted = change.uptimeMillis - down.uptimeMillis >= longPress
                        lifted = true
                    } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        lifted = false
                    }
                }
                lifted
            }
            if ((outcome == null || stillLifted) && titleBounds.contains(down.position)) {
                current.clear()
                editing = true
                if (outcome == null) while (awaitPointerEvent().changes.first().pressed) Unit
                return@awaitEachGesture
            }
            var lifted = outcome == true
            while (!lifted) {
                val change = awaitPointerEvent().changes.first()
                change.consume()
                current += change.position
                lifted = !change.pressed
            }
            if (current.size > 1) strokes += current.toList()
            current.clear()
            readJob = scope.launch {
                delay(STROKE_PAUSE_MILLIS)
                read()
            }
        }
    }

    val strokeColor = when (tint) {
        Tint.OK -> JuiceColors.Ok
        Tint.X -> JuiceColors.No
        Tint.BAD -> JuiceColors.Muted
        null -> hud.accent
    }

    Box(
        modifier
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(30.dp))
            .background(JuiceColors.white(0.14f))
            .border(1.dp, JuiceColors.white(0.28f), RoundedCornerShape(30.dp))
            .testTag(TAG_CENTRE_CARD)
            .onGloballyPositioned { cardOrigin = it.positionInRoot() }
            .then(drawing),
    ) {
        if (item == null) {
            Text(
                stringResource(R.string.overlay_empty),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                color = JuiceColors.white(0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = flyX.value * density
                        translationY = flyY.value * density
                        alpha = flyAlpha.value
                        rotationZ = flyX.value / 10f
                        val s = 1f + flyY.value / 600f
                        scaleX = s
                        scaleY = s
                    }
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(JuiceColors.of(item.type))
                            .clickable {
                                state.cycleType(item)
                                actions.feedback(FeedbackEvent.SNAP)
                            }
                            .padding(horizontal = 12.dp)
                            .testTag(TAG_TYPE_PILL),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.type.name.uppercase(), color = JuiceColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    if (item.id in state.parsing) ParsingChip(onDeviceOnly)
                }
                Spacer(Modifier.height(24.dp))
                if (editing) {
                    TitleField(item.title) { title ->
                        state.setTitle(item, title)
                        editing = false
                    }
                } else {
                    Text(
                        item.title,
                        fontSize = 25.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.onGloballyPositioned { titleBounds = it.boundsInRoot().translate(-cardOrigin) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(metaOf(state, item), fontSize = 14.sp, lineHeight = 20.sp, color = JuiceColors.white(0.82f))
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(worthOf(item, hud), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = hud.accent)
                }
                Spacer(Modifier.height(16.dp))
                if (tapButtons) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(
                            stringResource(R.string.overlay_complete), hud.accent, JuiceColors.Ink,
                            Modifier.weight(1f).testTag(TAG_COMPLETE),
                        ) { act(complete = true) }
                        PillButton(
                            stringResource(R.string.overlay_dismiss), JuiceColors.white(0.16f), Color.White,
                            Modifier.width(96.dp).testTag(TAG_DISMISS),
                        ) { act(complete = false) }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    hint ?: stringResource(R.string.overlay_card_hint),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = JuiceColors.white(0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val all = strokes + if (current.size > 1) listOf(current.toList()) else emptyList()
            all.forEach { stroke ->
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    stroke.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, strokeColor.copy(alpha = 0.35f), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, strokeColor, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ParsingChip(onDeviceOnly: Boolean) {
    val shimmer by rememberInfiniteTransition(label = "parse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "shimmer",
    )
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    0f to JuiceColors.white(0.1f), shimmer to JuiceColors.white(0.3f), 1f to JuiceColors.white(0.1f),
                ),
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (onDeviceOnly) R.string.overlay_parsing_local else R.string.overlay_parsing_cloud),
            fontSize = 11.sp,
            color = JuiceColors.white(0.9f),
        )
    }
}

@Composable
private fun TitleField(title: String, onDone: (String) -> Unit) {
    var text by remember(title) { mutableStateOf(title) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = false,
        textStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JuiceColors.white(0.1f))
            .padding(8.dp)
            .focusRequester(focus)
            .testTag(TAG_TITLE_FIELD),
    )
}

@Composable
internal fun PillButton(label: String, background: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun metaOf(state: OverlayShellState, item: Item): String {
    val parent = item.parentId?.let { id -> state.items.firstOrNull { it.id == id } }
    return when {
        item.type == ItemType.Project -> state.stepsOf(item).size.let { left ->
            if (left == 0) "All steps done. Tick it off." else "$left ${if (left == 1) "step" else "steps"} to go"
        }
        item.deadline != null -> "Due ${deadlineLabel(item.deadline!!)}"
        parent != null -> "Step of ${parent.title}"
        item.type == ItemType.Note -> "No deadline. Notes are quick catches."
        else -> "Add a time to make it a Goal."
    }
}

private fun worthOf(item: Item, hud: Hud): String {
    val m = hud.multiplierLabel
    return if (item.type == ItemType.Project) "${hud.baseXp(item.type)} x $m on finish"
    else "${hud.baseXp(item.type)} x $m = ${hud.worth(item.type)} Juice"
}
