package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.gesture.GesturePoint
import com.juice.core.gesture.GestureRecognizer
import com.juice.core.gesture.GestureResult
import com.juice.core.intake.ChipKind
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import java.time.Clock
import com.juice.feedback.FeedbackEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull



/** Pause after the last stroke before the drawing is read, so an X can take two strokes. */
const val STROKE_PAUSE_MILLIS = 420L

/** Extra wait when two to four strokes read as nothing yet, so a star drawn as five spokes can be finished. */
const val STAR_STROKE_PAUSE_MILLIS = 400L

/** A swipe has to cross this share of the card's width. */
private const val SWIPE_FRACTION = 0.3f
const val FLY_MILLIS = 300

private enum class Tint { OK, X, BAD, STAR }

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
    reservoir: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onEditingChange: (Boolean) -> Unit = {},
    clock: Clock = Clock.systemDefaultZone(),
) {
    val recognizer = remember { GestureRecognizer() }
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var tint by remember { mutableStateOf<Tint?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    val editing = item != null && state.titleEdit?.itemId == item.id
    val reportEditing by rememberUpdatedState(onEditingChange)
    LaunchedEffect(editing) { reportEditing(editing) }
    DisposableEffect(Unit) { onDispose { reportEditing(false) } }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    // The words that sorted the card, as chips; only shown when the classifier still agrees with the card's type.
    val chips = remember(item?.title, item?.type) {
        item?.let { titleChips(it) }.orEmpty()
    }
    var chipBounds by remember(chips) { mutableStateOf<List<Rect>>(emptyList()) }
    var bodyBounds by remember { mutableStateOf(Rect.Zero) }
    val shake = remember { Animatable(0f) }
    val flyX = remember(item?.id) { Animatable(0f) }
    val flyY = remember(item?.id) { Animatable(0f) }
    val flyAlpha = remember(item?.id) { Animatable(1f) }
    var busy by remember(item?.id) { mutableStateOf(false) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    val notRecognised = stringResource(R.string.overlay_not_recognised)
    val archived = stringResource(R.string.overlay_archived)
    val starredText = stringResource(R.string.overlay_starred)

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
        val result = recognizer.recogniseStrokes(drawn.map { stroke -> stroke.map { GesturePoint(it.x.toDouble(), it.y.toDouble()) } })
        tint = when (result) {
            is GestureResult.Checkmark -> Tint.OK
            is GestureResult.Dismiss -> Tint.X
            is GestureResult.Star -> Tint.STAR
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
            is GestureResult.Star -> if (item != null) {
                actions.feedback(FeedbackEvent.CHECKMARK)
                state.star(item)
                effects.float(starredText)
            }
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
            // Tapping or holding still on the title edits it; moving starts a stroke.
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
            if (outcome != false && (titleBounds.contains(down.position) || bodyBounds.contains(down.position))) {
                current.clear()
                val tappedChip = if (outcome == true && !stillLifted && titleBounds.contains(down.position)) {
                    chips.zip(chipBounds).firstOrNull { (_, bounds) -> bounds.translate(titleBounds.topLeft).contains(down.position) }?.first
                } else null
                if (item != null) {
                    if (tappedChip != null && tappedChip.span.kind != ChipKind.Verb) state.openChipPicker(item, tappedChip.span)
                    else state.beginTitleEdit(item)
                }
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
            // A long sideways swipe picks the card up, or puts a held one down; anything else is a drawing.
            val swipe = if (strokes.isEmpty()) {
                swipeOf(current.map { GesturePoint(it.x.toDouble(), it.y.toDouble()) }, (size.width * SWIPE_FRACTION).toDouble())
            } else null
            if (swipe == Swipe.RIGHT && state.held == null) {
                current.clear()
                state.pickUp(item)
                actions.feedback(FeedbackEvent.SNAP)
                return@awaitEachGesture
            }
            if (swipe == Swipe.LEFT && state.held != null) {
                current.clear()
                state.drop()
                actions.feedback(FeedbackEvent.CAPTURE)
                return@awaitEachGesture
            }
            if (current.size > 1) strokes += current.toList()
            current.clear()
            readJob = scope.launch {
                delay(STROKE_PAUSE_MILLIS)
                val unfinished = strokes.size in 2..4 && recognizer.recogniseStrokes(
                    strokes.map { stroke -> stroke.map { GesturePoint(it.x.toDouble(), it.y.toDouble()) } },
                ) == GestureResult.NotRecognised
                if (unfinished) delay(STAR_STROKE_PAUSE_MILLIS)
                read()
            }
        }
    }

    // Only watches, on the Initial pass, so the drawing gesture above sees every event untouched.
    val liquid = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val touch = { phase: LiquidTouchPhase, at: Offset -> actions.onLiquidTouch(LiquidTouch(phase, at, size.toSize())) }
            touch(LiquidTouchPhase.DOWN, down.position)
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.first()
                if (!change.pressed) break
                touch(LiquidTouchPhase.MOVE, change.position)
            }
            touch(LiquidTouchPhase.UP, down.position)
        }
    }

    val strokeColor = when (tint) {
        Tint.OK -> JuiceColors.Ok
        Tint.X -> JuiceColors.No
        Tint.BAD -> JuiceColors.Muted
        Tint.STAR -> JuiceColors.Ok
        null -> hud.accent
    }

    Box(
        modifier
            .graphicsLayer {
                translationX = shake.value
            }
            .roundedClip(RoundedCornerShape(30.dp))
            .background(JuiceColors.white(0.14f))
            .border(1.dp, JuiceColors.white(0.28f), RoundedCornerShape(30.dp))
            .testTag(TAG_CENTRE_CARD)
            .onGloballyPositioned { cardOrigin = it.positionInRoot() }
            .then(liquid)
            .then(drawing),
    ) {
        // The juice sits behind the text, outside the Column that flies away.
        Box(Modifier.fillMaxSize().testTag(TAG_RESERVOIR)) { reservoir(Modifier.fillMaxSize()) }
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
            // A short, wide card (a phone on its side) puts the title and list beside the worth and buttons.
            BoxWithConstraints(
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
                    },
            ) {
                val wide = maxHeight < WIDE_CARD_MAX_HEIGHT && maxWidth >= WIDE_CARD_MIN_WIDTH
                val header = @Composable {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var typeMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            Box(
                                Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(JuiceColors.of(item.type))
                                    .clickable { typeMenuOpen = true }
                                    .padding(horizontal = 12.dp)
                                    .testTag(TAG_TYPE_PILL),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item.type.name.uppercase(), color = JuiceColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                            DropdownMenu(
                                expanded = typeMenuOpen,
                                onDismissRequest = { typeMenuOpen = false },
                                shape = RoundedCornerShape(14.dp),
                                containerColor = JuiceColors.Sheet,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            ) {
                                ItemType.entries.forEach { type ->
                                    TypeMenuRow(
                                        type = type,
                                        current = type == item.type,
                                        onClick = {
                                            typeMenuOpen = false
                                            state.setType(item, type)
                                            actions.feedback(FeedbackEvent.SNAP)
                                        },
                                        modifier = Modifier.testTag(TAG_TYPE_OPTION_PREFIX + type.name),
                                    )
                                }
                            }
                        }
                        if (item.starredAt != null) {
                            Text(stringResource(R.string.overlay_starred_badge), color = hud.accent, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                        if (item.id in state.parsing) ParsingChip(onDeviceOnly)
                    }
                }
                val titleLine = listTitleLine(item)
                val title = @Composable {
                    if (editing) {
                        TitleField(item.title, onChange = state::changeTitleEdit) {
                            state.commitTitleEdit()
                        }
                    } else {
                        Text(
                            titleText(titleLine, chips.filter { it.end <= titleLine.length }),
                            fontSize = 25.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp,
                            maxLines = if (wide) 3 else 5,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layout ->
                                chipBounds = chips.map { layout.getPathForRange(it.start, it.end).getBounds() }
                            },
                            modifier = Modifier
                                .onGloballyPositioned { titleBounds = it.boundsInRoot().translate(-cardOrigin) }
                                .testTag(TAG_CARD_TITLE),
                        )
                    }
                }
                val body = @Composable { bodyModifier: Modifier ->
                    ListBody(
                        listBodyLines(item),
                        bodyModifier.onGloballyPositioned { bodyBounds = it.boundsInRoot().translate(-cardOrigin) },
                    )
                }
                val footer = @Composable {
                    item.completionCondition?.let { done ->
                        Text(
                            stringResource(R.string.overlay_done_when, done),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = JuiceColors.white(0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        metaOf(state, item),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = JuiceColors.white(0.82f),
                        modifier = if (item.type == ItemType.Routine && item.cadence != null) {
                            Modifier.testTag("cadenceLine").clickable { state.openRepeatPicker(item) }
                        } else Modifier,
                    )
                    reminderLabel(item, clock)?.let { at ->
                        Text(
                            stringResource(R.string.overlay_will_remind, at),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = JuiceColors.white(0.82f),
                            modifier = Modifier.testTag(TAG_REMINDER_LINE),
                        )
                    }
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
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            header()
                            Spacer(Modifier.height(10.dp))
                            title()
                            Spacer(Modifier.height(6.dp))
                            body(Modifier.weight(1f))
                        }
                        Column(Modifier.width(WIDE_CARD_SIDE).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                            footer()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)) {
                        header()
                        Spacer(Modifier.height(24.dp))
                        title()
                        Spacer(Modifier.height(12.dp))
                        body(Modifier.weight(1f))
                        footer()
                    }
                }
            }
        }
        ChipPickers(state)
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
