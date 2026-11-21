package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.card.CardOrder
import com.juice.core.category.Category
import com.juice.core.gesture.GesturePoint
import com.juice.core.gesture.GestureRecognizer
import com.juice.core.gesture.GestureResult
import com.juice.core.intake.ChipKind
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.card.createdStamp
import java.time.Clock
import java.time.LocalDate
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
const val FLY_MILLIS = 300

/** A first stroke has to cross this share of the card's width to move between categories. */
internal const val SWIPE_FRACTION = 0.25

private enum class Tint { OK, X, BAD, STAR, CIRCLE }

const val TAG_CREATED_STAMP = "created_stamp"

const val TAG_CREATED_STAMP = "created_stamp"

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
    onTitleSize: (TextUnit) -> Unit = {},
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
    var titleSize by remember { mutableStateOf(JuiceType.Title) }
    val autoSize = remember { CardTitleAutoSize { titleSize = it; onTitleSize(it) } }
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
    val newestFirstText = stringResource(R.string.overlay_order_newest)
    val oldestFirstText = stringResource(R.string.overlay_order_oldest)

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
            is GestureResult.Circle -> Tint.CIRCLE
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
            is GestureResult.Circle -> {
                // Anticlockwise is Newest first, clockwise Oldest first; the same order again only says its name.
                val order = if (result.clockwise) CardOrder.Oldest else CardOrder.Newest
                actions.feedback(FeedbackEvent.SNAP)
                if (state.cardOrder != order) actions.onSettingsChange { it.copy(cardOrder = order) }
                effects.float(if (result.clockwise) oldestFirstText else newestFirstText)
            }
            GestureResult.NotRecognised -> reject(notRecognised)
        }
    }

    // Left goes to the next category (a new one past the last), right to the previous; a held card is carried along.
    fun swipe(path: List<Offset>, minDistance: Double): Boolean {
        val direction = swipeOf(path.map { GesturePoint(it.x.toDouble(), it.y.toDouble()) }, minDistance) ?: return false
        val forward = direction == Swipe.LEFT
        val moved = when {
            state.held != null -> state.carryHeld(forward)
            forward -> if (state.nextCategory()) true else { state.beginNewCategory(); true }
            else -> state.previousCategory()
        }
        if (moved) {
            actions.feedback(FeedbackEvent.SNAP)
            actions.onScroll(if (forward) CATEGORY_SLOSH_STEPS else -CATEGORY_SLOSH_STEPS)
        } else if (!reduceMotion) scope.launch {
            // Nothing before Main: nudge the card and let it spring back.
            shake.animateTo(CATEGORY_NUDGE, tween(CATEGORY_NUDGE_MILLIS))
            shake.animateTo(0f, categoryNudgeBack())
        }
        return true
    }

    val drawing = Modifier.pointerInput(item?.id, busy, state.held, editing) {
        val minSwipe = size.width * SWIPE_FRACTION
        awaitEachGesture {
            val down = awaitFirstDown()
            if (busy) return@awaitEachGesture
            // The open title field owns its own drags, so they scroll it instead of drawing or swiping.
            if (editing && titleBounds.contains(down.position)) return@awaitEachGesture
            if (item == null) {
                // An empty category has nothing to draw on, but can still be swiped away from.
                down.consume()
                val path = mutableListOf(down.position)
                do {
                    val change = awaitPointerEvent().changes.first()
                    change.consume()
                    path += change.position
                } while (change.pressed)
                swipe(path, minSwipe)
                return@awaitEachGesture
            }
            readJob?.cancel()
            if (tint != null) { strokes.clear(); tint = null }
            current.clear()
            current += down.position
            down.consume()
            // A press held still past the long-press time picks the card up; a quick tap edits the title; moving starts a stroke.
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
            val longPressed = outcome == null || stillLifted
            val holding = state.held != null
            if (holding || longPressed) {
                val path = current.toMutableList()
                current.clear()
                if (holding) {
                    // A held card is put down by a tap on the centre card, or carried to the next category by a swipe.
                    if (outcome == true && !stillLifted) {
                        state.drop()
                        actions.feedback(FeedbackEvent.CAPTURE)
                    }
                } else {
                    state.pickUp(item)
                    actions.feedback(FeedbackEvent.SNAP)
                }
                if (outcome == null || outcome == false) {
                    do {
                        val change = awaitPointerEvent().changes.first()
                        path += change.position
                    } while (change.pressed)
                    if (holding && outcome == false) swipe(path, minSwipe)
                }
                return@awaitEachGesture
            }
            if (outcome == true && (titleBounds.contains(down.position) || bodyBounds.contains(down.position))) {
                current.clear()
                val tappedChip = if (titleBounds.contains(down.position)) {
                    chips.zip(chipBounds).firstOrNull { (_, bounds) -> bounds.translate(titleBounds.topLeft).contains(down.position) }?.first
                } else null
                if (tappedChip != null && tappedChip.span.kind != ChipKind.Verb) state.openChipPicker(item, tappedChip.span)
                else state.beginTitleEdit(item)
                return@awaitEachGesture
            }
            var lifted = outcome == true
            while (!lifted) {
                val change = awaitPointerEvent().changes.first()
                change.consume()
                current += change.position
                lifted = !change.pressed
            }
            // The first stroke, read at lift: a long sideways one moves between categories instead of being drawn.
            if (strokes.isEmpty() && swipe(current.toList(), minSwipe)) {
                current.clear()
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
        Tint.CIRCLE -> hud.accent
        null -> hud.accent
    }

    Box(
        modifier
            .graphicsLayer {
                translationX = shake.value
            }
            .roundedClip(JuiceShapes.Card)
            .background(JuiceColors.white(0.14f))
            .border(1.dp, JuiceColors.white(0.28f), JuiceShapes.Card)
            .testTag(TAG_CENTRE_CARD)
            .onGloballyPositioned { cardOrigin = it.positionInRoot() }
            .then(liquid)
            .then(drawing),
    ) {
        // The juice sits behind the text, outside the Column that flies away.
        Box(Modifier.fillMaxSize().testTag(TAG_RESERVOIR)) { reservoir(Modifier.fillMaxSize()) }
        if (item == null) {
            Text(
                if (state.category.id == Category.MAIN_ID) stringResource(R.string.overlay_empty)
                else stringResource(R.string.overlay_category_empty, state.category.name),
                fontSize = JuiceType.Dialogue,
                lineHeight = JuiceType.DialogueLine,
                color = JuiceColors.white(0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    .testTag(if (state.category.id == Category.MAIN_ID) TAG_OVERLAY_EMPTY else TAG_CATEGORY_EMPTY),
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
                            Box(Modifier.testTag(TAG_TYPE_PILL).touchTarget({ typeMenuOpen = true }), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .height(28.dp)
                                        .testTag(TAG_TYPE_PILL_VISIBLE)
                                        .clip(JuiceShapes.Capsule)
                                        .background(JuiceColors.of(item.type))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(item.type.name.uppercase(), color = JuiceColors.Ink, fontSize = JuiceType.Badge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                            }
                            DropdownMenu(
                                expanded = typeMenuOpen,
                                onDismissRequest = { typeMenuOpen = false },
                                shape = JuiceShapes.Badge,
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
                            Text(stringResource(R.string.overlay_starred_badge), color = hud.accent, fontSize = JuiceType.Dialogue, modifier = Modifier.padding(start = 8.dp))
                        }
                        if (item.id in state.parsing) ParsingChip(onDeviceOnly)
                    }
                }
                val titleLine = listTitleLine(item)
                val title = @Composable { titleModifier: Modifier ->
                    if (editing) {
                        TitleField(
                            item.title,
                            onChange = state::changeTitleEdit,
                            fontSize = titleSize,
                            modifier = titleModifier.onGloballyPositioned { titleBounds = it.boundsInRoot().translate(-cardOrigin) },
                        ) {
                            state.commitTitleEdit()
                        }
                    } else {
                        BasicText(
                            titleText(titleLine, chips.filter { it.end <= titleLine.length }),
                            style = TextStyle(
                                fontFamily = SpaceGrotesk,
                                fontSize = JuiceType.Title,
                                lineHeight = JuiceType.TitleLineRatio,
                                lineHeightStyle = TitleLineHeightStyle,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.5).sp,
                                color = Color.White,
                            ),
                            overflow = TextOverflow.Ellipsis,
                            autoSize = autoSize,
                            onTextLayout = { layout ->
                                chipBounds = chips.map { layout.getPathForRange(it.start, it.end).getBounds() }
                            },
                            modifier = titleModifier
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
                            fontSize = JuiceType.Body,
                            lineHeight = JuiceType.BodyLine,
                            color = JuiceColors.white(0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        metaOf(state, item),
                        fontSize = JuiceType.Body,
                        lineHeight = JuiceType.BodyLine,
                        color = JuiceColors.white(0.82f),
                        modifier = if (item.type == ItemType.Routine && item.cadence != null) {
                            Modifier.testTag("cadenceLine").clickable { state.openRepeatPicker(item) }
                        } else Modifier,
                    )
                    reminderLabel(item, clock)?.let { at ->
                        Text(
                            stringResource(R.string.overlay_will_remind, at),
                            fontSize = JuiceType.Body,
                            lineHeight = JuiceType.BodyLine,
                            color = JuiceColors.white(0.82f),
                            modifier = Modifier.testTag(TAG_REMINDER_LINE),
                        )
                    }
                    item.createdAt?.let { created ->
                        Text(
                            createdStamp(created, clock.zone, LocalDate.now(clock)),
                            fontSize = JuiceType.Caption,
                            lineHeight = JuiceType.CaptionLine,
                            color = JuiceColors.white(0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp).testTag(TAG_CREATED_STAMP),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(JuiceShapes.Capsule)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(worthOf(item, hud), fontSize = JuiceType.Label, fontWeight = FontWeight.Bold, color = hud.accent)
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
                        fontSize = JuiceType.Caption,
                        lineHeight = JuiceType.CaptionLine,
                        color = JuiceColors.white(0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val bodyWeight = if (listBodyLines(item).isEmpty()) 0.001f else 1f
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            header()
                            Spacer(Modifier.height(10.dp))
                            title(Modifier.weight(1f, fill = editing))
                            Spacer(Modifier.height(6.dp))
                            if (!editing) body(Modifier.weight(bodyWeight))
                        }
                        Column(Modifier.width(WIDE_CARD_SIDE).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                            footer()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)) {
                        header()
                        Spacer(Modifier.height(24.dp))
                        title(Modifier.weight(1f, fill = editing))
                        Spacer(Modifier.height(12.dp))
                        if (!editing) body(Modifier.weight(bodyWeight))
                        footer()
                    }
                }
            }
        }
        ChipPickers(state, hud, actions)
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

/** The visible type pill inside its 44 dp touch target. */
internal const val TAG_TYPE_PILL_VISIBLE = "type_pill_visible"
