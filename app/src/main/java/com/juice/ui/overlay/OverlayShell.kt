package com.juice.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.model.Item
import com.juice.feedback.FeedbackEvent
import com.juice.settings.CharacterSettings
import com.juice.settings.JuiceSettings
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import java.time.Clock
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import com.juice.ui.Tier
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TAG_CAPTURE_FIELD = "capture_field"
const val TAG_ADD = "add_button"
const val TAG_CENTRE_CARD = "centre_card"
const val TAG_RESERVOIR = "reservoir"
const val TAG_HYDRATION = "hydration_label"
const val TAG_JUICE_BOX = "juice_box"
const val TAG_MULTIPLIER = "multiplier_badge"
const val TAG_UNDO = "undo"
const val TAG_COMPLETE = "complete_button"
const val TAG_DISMISS = "dismiss_button"
const val TAG_TITLE_FIELD = "title_field"
const val TAG_TYPE_PILL = "type_pill"
const val TAG_CLOSE = "close_handle"
const val TAG_CAROUSEL = "carousel"
const val TAG_RECAP = "recap_button"
const val TAG_ROUTINES = "routines_button"
const val TAG_SETTINGS = "settings_button"
const val TAG_SETTINGS_DONE = "settings_done"
const val TAG_FLAVOUR_PREFIX = "flavour_"
const val TAG_SWIPE_DISTANCE_PREFIX = "swipe_distance_"
const val TAG_BANNER = "banner"
const val MAX_CARD_SCALE = 1.15f

/** True when [y] lies between the first visible item's start and the last one's end but on no item: a gap between cards. */
internal fun tapIsInCarouselGap(y: Float, itemSpans: List<IntRange>): Boolean {
    if (itemSpans.isEmpty()) return false
    val first = itemSpans.minOf { it.first }
    val last = itemSpans.maxOf { it.last }
    return y > first && y < last && itemSpans.none { y >= it.first && y <= it.last }
}

private const val CAROUSEL_ITEM_DP = 56
private const val CAROUSEL_GAP_DP = 8
private const val BANNER_MILLIS = 2200L
private const val FLOAT_MILLIS = 1300
private const val RING_PREVIEW_MILLIS = 1400L
private val BACKDROP_DP = 280.dp
private const val WIGGLE_MILLIS = 110
private const val WIGGLE_DEGREES = 1.5f
private const val WIGGLE_DP = 1.5f
private const val HELD_ALPHA = 0.4f

/** Scale of a card whose centre is [distance] px from the focal dot, falling off over [reach] px. */
internal fun focalScale(distance: Float, reach: Float): Float =
    1f + (MAX_CARD_SCALE - 1f) * (1f - (abs(distance) / reach).coerceIn(0f, 1f))

/**
 * The overlay: header, snapping carousel, the centre card where ✓/✗ are drawn, the undo prompt,
 * the hydration label and the capture field, plus its settings sheet (design doc §5.1).
 * [reservoir] draws the liquid behind the centre card; the default is a plain fill for tests and previews.
 */
@Composable
fun OverlayShell(
    state: OverlayShellState,
    modifier: Modifier = Modifier,
    settings: JuiceSettings = JuiceSettings(),
    hud: Hud = Hud(),
    blurred: Boolean = false,
    actions: OverlayActions = OverlayActions(),
    effects: OverlayEffects = remember { OverlayEffects() },
    reservoir: @Composable (Modifier) -> Unit = { PlainReservoir(hud, it) },
    /** Room kept clear for the status and navigation bars, measured once by the host so nothing jumps. */
    systemBars: PaddingValues = PaddingValues(0.dp),
    /** Lets the host's BACK key leave writing mode instead of closing the overlay. */
    writingBack: WritingBack = remember { WritingBack() },
    /** The Characters section of the settings sheet; left out when null. */
    characters: CharacterSettings? = null,
    /** Decides which day, week and month the Recap covers. */
    clock: Clock = Clock.systemDefaultZone(),
    /** True once the host has asked to close: the content pops away, the ground drains, then [onClosed] is called. */
    closing: Boolean = false,
    onClosed: () -> Unit = {},
    /** Called when the ground starts to drain, after the content has popped away and before [onClosed]. */
    onDraining: () -> Unit = {},
    /** Reports how far the ground has grown, 0..1: rising on open, falling on the drain at close. */
    onRevealProgress: (Float) -> Unit = {},
) {
    val barTop = systemBars.calculateTopPadding()
    val barBottom = systemBars.calculateBottomPadding()
    // The keyboard is the one inset still followed live; the content sits above whichever is taller.
    val contentBottom = maxOf(barBottom, WindowInsets.ime.asPaddingValues().calculateBottomPadding())
    var settingsOpen by remember { mutableStateOf(false) }
    var recapOpen by remember { mutableStateOf(false) }
    var routinesOpen by remember { mutableStateOf(false) }
    var centre by remember { mutableStateOf(0) }
    // Writing mode: the capture field has focus and fills the screen; the rest of the layout is not composed.
    var writing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // The keyboard going away does not drop focus, so leaving writing mode is explicit; the draft is kept.
    fun leaveWriting() {
        focus.clearFocus()
        writing = false
    }
    SideEffect {
        writingBack.isWriting = { writing }
        writingBack.leave = ::leaveWriting
        writingBack.isRecapOpen = { recapOpen }
        writingBack.closeRecap = { recapOpen = false }
        writingBack.isRoutinesOpen = { routinesOpen }
        writingBack.closeRoutines = { routinesOpen = false }
    }
    // The keyboard swallows the first back press to hide itself, so the app only sees it close.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible && writing) leaveWriting()
        imeWasVisible = imeVisible
    }
    // The ground grows first, then the content rises in; both are fully shown on the first frame with reduced motion.
    val spin = remember(settings.reduceMotion) { SpinMomentum(enabled = !settings.reduceMotion) }
    val reveal = remember { Animatable(if (settings.reduceMotion) 1f else 0f) }
    val entrance = remember { Animatable(if (settings.reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!settings.reduceMotion) {
            reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
            entrance.animateTo(1f, tween(ENTRANCE_TOTAL_MILLIS, easing = LinearEasing))
        }
    }
    val currentOnRevealProgress by rememberUpdatedState(onRevealProgress)
    LaunchedEffect(Unit) {
        snapshotFlow { reveal.value }.collect { currentOnRevealProgress(it) }
    }
    val entranceProgress: () -> Float = { entrance.value }
    val pop = remember { Animatable(0f) }
    val currentOnClosed by rememberUpdatedState(onClosed)
    val currentOnDraining by rememberUpdatedState(onDraining)
    val cardPops = remember { CardPops() }
    // Cancelled with the composition, so tearing the overlay down mid-close never calls onClosed.
    LaunchedEffect(closing) {
        if (closing) {
            if (!settings.reduceMotion) {
                // The visible cards pop top to bottom, then the centre card and the capture field, each with its sound;
                // the froth (the shared pop) starts with the centre card.
                val visible = cardPops.visibleIndices()
                cardPops.popping = visible
                val total = elementPopStartMillis(visible.size, 1) + CARD_POP_MILLIS
                coroutineScope {
                    launch { cardPops.elapsed.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) }
                    repeat(visible.size + 2) { i ->
                        if (i > 0) delay(CARD_POP_GAP_MILLIS.toLong())
                        actions.feedback(FeedbackEvent.CARD_POP)
                        if (i == visible.size) launch { pop.animateTo(1f, tween(POP_MILLIS, easing = LinearEasing)) }
                    }
                }
                currentOnDraining()
                reveal.animateTo(0f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
            }
            currentOnClosed()
        }
    }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val overshoot = revealOriginOvershootPx(LocalDensity.current)
    var ringPreview by remember { mutableStateOf(0) }
    LaunchedEffect(ringPreview) {
        if (ringPreview > 0) {
            delay(RING_PREVIEW_MILLIS)
            ringPreview = 0
        }
    }
    val undo = state.undo
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(OverlayShellState.UNDO_WINDOW_MILLIS)
            state.expireUndo(undo)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            actions.onTick()
        }
    }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = SpaceGrotesk, color = Color.White)) {
        Box(
            modifier
                .fillMaxSize(),
        ) {
            SliceBackdrop(
                hud,
                slices = SliceLogoPainter.sliceCount(state.items.size),
                active = centre.coerceAtMost(state.items.lastIndex),
                spin = spin,
                shown = { reveal.value >= 1f },
            )
            // The ground sits in front of the slice, so the slice is dimmed by it; it grows from the reveal origin.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val ground = overlayTint(blurred)
                        val progress = reveal.value
                        if (progress >= 1f) {
                            drawRect(ground)
                        } else {
                            val origin = revealOrigin(size.width, overshoot)
                            val radius = revealRadius(size.width, size.height, origin) * progress
                            clipPath(Path().apply { addOval(Rect(origin, radius)) }) { drawRect(ground) }
                        }
                    },
            )
            // Empty background: a tap closes. Cards, buttons and the field take their own taps first.
            val closeLabel = stringResource(R.string.overlay_close)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { actions.onClose() } }
                    .semantics { contentDescription = closeLabel }
                    .testTag(TAG_CLOSE),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { contentSize = it },
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(
                        // Outside the field and the plus button, a tap ends writing mode.
                        if (writing) Modifier.pointerInput(Unit) { detectTapGestures { leaveWriting() } }
                        else Modifier,
                    )
                    .padding(start = 14.dp, end = 14.dp, top = barTop + 38.dp, bottom = contentBottom + 16.dp),
            ) {
                if (!writing) {
                    Header(hud, entranceProgress, cardPops.element(0), onRecap = { recapOpen = true }, onRoutines = { state.routines.observe(); routinesOpen = true }, onSettings = { settingsOpen = true })
                    Spacer(Modifier.height(12.dp))
                    val list = state.items
                    CardRow(
                        state, hud, settings, actions, effects, reservoir, centre,
                        onCentre = { centre = it }, modifier = Modifier.weight(1f).fillMaxWidth(),
                        entrance = entranceProgress, spin = spin, cardPops = cardPops,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(44.dp).entrance(entranceProgress).then(cardPops.element(0))) {
                        if (undo != null) UndoToast(undo, hud) {
                            state.undoLast()
                            actions.feedback(FeedbackEvent.UNDO)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Capture(
                    hud,
                    writing = writing,
                    onWritingChange = { writing = it },
                    onLeave = ::leaveWriting,
                    modifier = (if (writing) Modifier.weight(1f) else Modifier.entrance(entranceProgress)).then(cardPops.element(1)),
                ) { text ->
                    if (state.capture(text) != null) actions.feedback(FeedbackEvent.CAPTURE)
                }
            }
            val chat = state.coachChat
            if (chat != null && chat.open) {
                CoachChat(
                    chat, hud,
                    Modifier
                        .fillMaxSize()
                        .then(cardPops.element(1))
                        .background(overlayTint(blurred))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                        .padding(start = 14.dp, end = 14.dp, top = barTop + 38.dp, bottom = contentBottom + 16.dp),
                )
            }
            state.creator?.let { creator ->
                CreatorFlow(
                    creator, hud, onConfirm = state::createCharacter, onCancel = state::cancelCreator,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayTint(blurred))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                        .padding(start = 14.dp, end = 14.dp, top = barTop + 38.dp, bottom = contentBottom + 16.dp),
                )
            }
            val needsCloud = stringResource(R.string.coach_needs_cloud)
            val unreachable = stringResource(R.string.coach_unreachable)
            LaunchedEffect(chat?.notice) {
                when (chat?.notice) {
                    CoachNotice.CloudRequired -> effects.banner(needsCloud)
                    CoachNotice.Unreachable -> effects.banner(unreachable)
                    null -> {}
                }
            }
            Banner(effects, hud, Modifier.align(Alignment.TopCenter).padding(top = barTop + 92.dp))
            AnimatedVisibility(settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { settingsOpen = false },
                )
            }
            AnimatedVisibility(
                settingsOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                SettingsSheet(
                    settings, hud, actions,
                    onDone = { settingsOpen = false },
                    onSwipeDistance = { ringPreview++ },
                    characters = characters,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = barTop + 120.dp)
                        .padding(bottom = barBottom),
                )
            }
            if (routinesOpen) {
                RoutinesView(
                    state.routines, hud, settings, actions, effects, reservoir,
                    onClose = { routinesOpen = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayTint(blurred))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                        .padding(start = 14.dp, end = 14.dp, top = barTop + 38.dp, bottom = contentBottom + 16.dp),
                )
            }
            if (recapOpen) {
                RecapScreen(
                    hud, settings.reduceMotion, clock, actions,
                    onClose = { recapOpen = false },
                    systemBars = systemBars,
                )
            }
            if (ringPreview > 0) SwipeRing(settings, hud)
            }
            if (pop.value > 0f) {
                // Froth bursts from the carousel, the centre card and the capture field.
                val bubbles = remember(contentSize) {
                    val w = contentSize.width.toFloat()
                    val h = contentSize.height.toFloat()
                    val origins = listOf(Offset(w * 0.2f, h * 0.5f), Offset(w * 0.65f, h * 0.45f), Offset(w * 0.5f, h * 0.9f))
                    frothBubbles(origins, FROTH_SEED, maxDistance = w * 0.35f, maxRadius = w * 0.04f)
                }
                Canvas(Modifier.fillMaxSize()) {
                    bubbles.forEach { bubble ->
                        val (centre, radius) = bubble.at(pop.value)
                        drawCircle(hud.accent.copy(alpha = 0.7f), radius, centre)
                    }
                }
            }
            if (closing) {
                // A second tap must not restart or double-close while the overlay pops away.
                Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } })
            }
        }
    }
}

private const val FROTH_SEED = 44L

/** The carousel's cards popping away on close: which of them are on screen, top to bottom, and how far the popping has run. */
internal class CardPops {
    var visibleIndices: () -> List<Int> = { emptyList() }
    var popping by mutableStateOf<List<Int>>(emptyList())
    val elapsed = Animatable(0f)

    /** A pop for a non-card element once the cards are gone: slot 0 is the centre card, slot 1 the capture field. */
    fun element(slot: Int): Modifier = Modifier.graphicsLayer {
        val p = elementPopProgress(elapsed.value, popping.size, slot)
        if (p > 0f) {
            scaleX = popScale(p)
            scaleY = popScale(p)
            alpha = popAlpha(p)
        }
    }
}

/** The carousel beside the centre card for the items of [state]; shared by the main view and the Routines view. */
@Composable
internal fun CardRow(
    state: OverlayShellState,
    hud: Hud,
    settings: JuiceSettings,
    actions: OverlayActions,
    effects: OverlayEffects,
    reservoir: @Composable (Modifier) -> Unit,
    centre: Int,
    onCentre: (Int) -> Unit,
    modifier: Modifier = Modifier,
    entrance: () -> Float = { 1f },
    spin: SpinMomentum = remember { SpinMomentum() },
    cardPops: CardPops = remember { CardPops() },
) {
    val list = state.items
    var editing by remember { mutableStateOf(false) }
    Row(modifier) {
        if (list.isNotEmpty()) {
            // Stays composed at zero width while a title is edited: leaving would reset its scroll and re-centre item 0.
            Carousel(list, hud, actions, state, settings.reduceMotion, entrance, onCentre = onCentre, spin = spin, cardPops = cardPops, modifier = Modifier.width(if (editing) 0.dp else 112.dp).fillMaxHeight())
            if (!editing) Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight().entrance(entrance).then(cardPops.element(0))) {
            HydrationLabel(hud, Modifier.padding(bottom = 6.dp))
            Box(Modifier.weight(1f).fillMaxWidth().testTag(TAG_JUICE_BOX)) {
                CentreCard(
                    state = state,
                    item = list.getOrNull(centre.coerceAtMost(list.lastIndex)),
                    hud = hud,
                    tapButtons = settings.tapButtons,
                    reduceMotion = settings.reduceMotion,
                    onDeviceOnly = settings.onDeviceOnly,
                    actions = actions,
                    effects = effects,
                    reservoir = reservoir,
                    modifier = Modifier.fillMaxSize(),
                    onEditingChange = { editing = it },
                )
                Floats(effects, hud, Modifier.fillMaxSize())
            }
        }
    }
}

/** The ground's alpha: lighter than the old 0.55 and 0.97, so the screen behind shows through. */
internal fun overlayGroundAlpha(blurred: Boolean): Float = if (blurred) 0.35f else 0.75f

/**
 * The overlay's ground: the mockup's dark teal-to-indigo glass. Over the blur it stays thin
 * enough for the blurred screen to show through; at 0.9 it hid the blur completely. It is drawn over the
 * slice backdrop and also backs the panels, so it stays light but dark enough for white text.
 */
private fun overlayTint(blurred: Boolean): Brush {
    val alpha = overlayGroundAlpha(blurred)
    return Brush.linearGradient(
        listOf(Color(0xFF0F2E35), Color(0xFF0B1A22), Color(0xFF1A1838)).map { it.copy(alpha = alpha) },
    )
}

/**
 * [withFrameNanos] that tests can pause: Compose's own `withInfiniteAnimationFrameNanos` is internal, so go through the
 * public [InfiniteAnimationPolicy] it uses, which keeps this endless loop from blocking test idling.
 */
private suspend fun <R> withInfiniteFrameNanos(onFrame: (Long) -> R): R {
    val policy = coroutineContext[InfiniteAnimationPolicy] ?: return withFrameNanos(onFrame)
    return policy.onInfiniteOperation { withFrameNanos(onFrame) }
}

const val TAG_BACKDROP_SLICE = "backdrop_slice"
const val BACKDROP_SHOWN = "shown"
const val BACKDROP_HIDDEN = "hidden"

/**
 * The big slice in the top-right corner behind the content; it takes no touches. It is drawn sharp, as a
 * plain vector draw; the ground in front of it supplies any dimming or softening.
 */
@Composable
private fun SliceBackdrop(hud: Hud, slices: Int, active: Int, spin: SpinMomentum, shown: () -> Boolean) {
    val painter = remember { SliceLogoPainter() }
    val style = SliceStyle.of(hud.flavour)
    var turn by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spin) {
        if (!spin.enabled) return@LaunchedEffect
        var last = withInfiniteFrameNanos { it }
        while (true) {
            val now = withInfiniteFrameNanos { it }
            turn = spin.step(now - last)
            last = now
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .offset(BACKDROP_DP / 4, -BACKDROP_DP / 4)
                .size(BACKDROP_DP)
                .semantics { stateDescription = if (shown()) BACKDROP_SHOWN else BACKDROP_HIDDEN }
                .testTag(TAG_BACKDROP_SLICE)
                // Hidden while the stage's flying slice opens or closes the overlay, so one slice is on screen.
                .graphicsLayer {
                    alpha = if (shown()) 1f else 0f
                    rotationZ = turn
                },
        ) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, size.width / 2, size.height / 2, size.width, style, slices, active, shadow = false) }
        }
    }
}

/** Previews the swipe distance around the handle's saved position. */
@Composable
private fun SwipeRing(settings: JuiceSettings, hud: Hud) {
    val painter = remember { SliceLogoPainter() }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cx = constraints.maxWidth * settings.handleX.coerceIn(0f, 1f)
        val cy = constraints.maxHeight * settings.handleY.coerceIn(0f, 1f)
        val radius = with(density) { settings.swipeDistance.dp.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { painter.drawRing(it.nativeCanvas, cx, cy, radius, 0f, false, hud.accent.toArgb(), density.density) }
        }
    }
}

@Composable
private fun Header(hud: Hud, entrance: () -> Float, pop: Modifier, onRecap: () -> Unit, onRoutines: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).entrance(entrance, fromBottom = false).then(pop), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
        Column(Modifier.widthIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text(stringResource(R.string.overlay_level, hud.level), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text(
                    hud.flavour.displayName,
                    fontSize = 13.sp,
                    color = JuiceColors.white(0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(JuiceColors.white(0.18f))) {
                Box(Modifier.fillMaxWidth(hud.levelProgress).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(hud.accent))
            }
        }
        }
        MultiplierBadge(hud)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(String.format("%,d", hud.juice), fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text(stringResource(R.string.overlay_juice_label), fontSize = 11.sp, letterSpacing = 1.sp, color = JuiceColors.white(0.75f))
        }
        Spacer(Modifier.width(8.dp))
        val recapLabel = stringResource(R.string.recap_open)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(JuiceColors.white(0.14f))
                .clickable(onClick = onRecap)
                .semantics { contentDescription = recapLabel }
                .testTag(TAG_RECAP),
            contentAlignment = Alignment.Center,
        ) {
            // Three bars stepping down, like a burndown.
            Canvas(Modifier.size(20.dp)) {
                val u = size.width / 24f
                fun bar(x: Float, top: Float) =
                    drawRoundRect(Color.White, Offset(x * u, top * u), androidx.compose.ui.geometry.Size(4f * u, (20f - top) * u), androidx.compose.ui.geometry.CornerRadius(u))
                bar(3f, 4f); bar(10f, 10f); bar(17f, 15f)
            }
        }
        Spacer(Modifier.width(8.dp))
        val routinesLabel = stringResource(R.string.routines_open)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(JuiceColors.white(0.14f))
                .clickable(onClick = onRoutines)
                .semantics { contentDescription = routinesLabel }
                .testTag(TAG_ROUTINES),
            contentAlignment = Alignment.Center,
        ) {
            // A circling arrow: the Routine comes round again.
            Canvas(Modifier.size(20.dp)) {
                val u = size.width / 24f
                drawArc(Color.White, 40f, 280f, false, Offset(4f * u, 4f * u), androidx.compose.ui.geometry.Size(16f * u, 16f * u), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(
                    Path().apply { moveTo(16f * u, 1f * u); lineTo(21f * u, 6f * u); lineTo(14f * u, 7f * u); close() },
                    Color.White,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val label = stringResource(R.string.settings_open)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(JuiceColors.white(0.14f))
                .clickable(onClick = onSettings)
                .semantics { contentDescription = label }
                .testTag(TAG_SETTINGS),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val w = 2.dp.toPx()
                val u = size.width / 24f
                fun line(x0: Float, y: Float, x1: Float) =
                    drawLine(Color.White, Offset(x0 * u, y * u), Offset(x1 * u, y * u), w, StrokeCap.Round)
                line(4f, 7f, 14f); line(18f, 7f, 20f); line(4f, 17f, 6f); line(10f, 17f, 20f)
                drawCircle(Color.White, 2f * u, Offset(16f * u, 7f * u), style = Stroke(w))
                drawCircle(Color.White, 2f * u, Offset(8f * u, 17f * u), style = Stroke(w))
            }
        }
    }
}

/** The left column: cards scale up near the focal dot and snap to it. Tap a card to bring it in. */
@Composable
private fun Carousel(
    items: List<Item>,
    hud: Hud,
    actions: OverlayActions,
    state: OverlayShellState,
    reduceMotion: Boolean,
    entrance: () -> Float,
    onCentre: (Int) -> Unit,
    spin: SpinMomentum,
    cardPops: CardPops,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    SideEffect { cardPops.visibleIndices = { listState.layoutInfo.visibleItemsInfo.map { it.index } } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val centreIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: 0
        }
    }
    var lastCentre by remember { mutableStateOf<Int?>(null) }
    // Only finger-driven scrolling turns the slice; animated and snap scrolls never do.
    val spinNow by rememberUpdatedState(spin)
    val spinConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) spinNow.drag(-available.y * DRAG_DEG_PER_PX)
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isBigSwipe(available.y)) spinNow.kick(spinBoostFor(-available.y))
                return Velocity.Zero
            }
        }
    }
    val held = state.held
    val heldIndex = items.indexOfFirst { it.id == held }
    // While a card is held, the card at the focal dot is where it would land.
    LaunchedEffect(centreIndex, held) { if (held != null) state.moveTarget(centreIndex) }
    val wiggle: State<Float> = if (held != null && !reduceMotion) {
        rememberInfiniteTransition(label = "wiggle")
            .animateFloat(-1f, 1f, infiniteRepeatable(tween(WIGGLE_MILLIS, easing = LinearEasing), RepeatMode.Reverse), label = "wiggle")
    } else {
        remember { mutableStateOf(0f) }
    }
    val lineColor = hud.accent
    LaunchedEffect(centreIndex) {
        onCentre(centreIndex)
        val previous = lastCentre
        lastCentre = centreIndex
        if (previous != null && previous != centreIndex) {
            actions.feedback(FeedbackEvent.SNAP)
            actions.onScroll(centreIndex - previous)
        }
    }
    // A capture lands first: bring it to the focal dot.
    val firstId = items.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId != null && centreIndex != 0) listState.animateScrollToItem(0)
    }
    // The list's scrolling takes every touch over its column, so empty space in it closes here; cards consume their own taps.
    BoxWithConstraints(
        modifier
            .pointerInput(spin) {
                // Initial pass and never consumes: a touch stops the spin without disturbing taps or reordering.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    spinNow.grab()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    spinNow.release()
                }
            }
            .pointerInput(held) {
                if (held == null) detectTapGestures { tap ->
                    val spans = listState.layoutInfo.visibleItemsInfo.map { it.offset..(it.offset + it.size) }
                    if (!tapIsInCarouselGap(tap.y, spans)) actions.onClose()
                }
            }
            .testTag(TAG_CAROUSEL),
    ) {
        val pad = (maxHeight - CAROUSEL_ITEM_DP.dp) / 2
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(top = pad, bottom = pad),
            verticalArrangement = Arrangement.spacedBy(CAROUSEL_GAP_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().nestedScroll(spinConnection).padding(start = 12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val isCentre = index == centreIndex
                Column(
                    Modifier
                        .entrance(entrance, index)
                        .size(width = 88.dp, height = CAROUSEL_ITEM_DP.dp)
                        .graphicsLayer {
                            val info = listState.layoutInfo
                            val own = info.visibleItemsInfo.firstOrNull { it.index == index }
                            if (own != null) {
                                val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val distance = own.offset + own.size / 2f - middle
                                val s = focalScale(distance, own.size * 1.2f)
                                val steps = abs(distance) / (own.size + with(density) { CAROUSEL_GAP_DP.dp.toPx() })
                                scaleX = s * (1f - steps * 0.08f).coerceAtLeast(0.78f)
                                scaleY = scaleX
                                alpha = (1f - steps * 0.26f).coerceIn(0.3f, 1f)
                            }
                            val rank = cardPops.popping.indexOf(index)
                            if (rank >= 0) {
                                val p = cardPopProgress(cardPops.elapsed.value, rank)
                                scaleX *= popScale(p)
                                scaleY *= popScale(p)
                                alpha *= popAlpha(p)
                            }
                            if (item.id == held) {
                                alpha *= HELD_ALPHA
                            } else if (held != null) {
                                rotationZ = wiggle.value * WIGGLE_DEGREES * if (index % 2 == 0) 1 else -1
                                translationX = wiggle.value * WIGGLE_DP.dp.toPx()
                            }
                        }
                        .drawBehind {
                            // The line sits in the gap between the focal card and the side the held card would land on.
                            if (held != null && index == state.target && heldIndex >= 0 && heldIndex != index) {
                                val y = if (index > heldIndex) size.height + CAROUSEL_GAP_DP.dp.toPx() / 2 else -CAROUSEL_GAP_DP.dp.toPx() / 2
                                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                            }
                        }
                        .roundedClip(RoundedCornerShape(16.dp))
                        .background(JuiceColors.white(if (isCentre) 0.24f else 0.12f))
                        .clickable {
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    val typeColor = JuiceColors.of(item.type)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(typeColor))
                        Spacer(Modifier.width(5.dp))
                        Text(item.type.name.uppercase(), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = typeColor)
                    }
                    Text(item.title, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.CenterStart).size(8.dp).clip(CircleShape).background(hud.accent))
    }
}

@Composable
internal fun UndoToast(undo: Undo, hud: Hud, onUndo: () -> Unit) {
    val left = remember(undo) { Animatable(1f) }
    LaunchedEffect(undo) { left.animateTo(0f, tween(OverlayShellState.UNDO_WINDOW_MILLIS.toInt(), easing = LinearEasing)) }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(JuiceColors.Toast)
            .border(1.dp, JuiceColors.white(0.18f), RoundedCornerShape(22.dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (undo.dismissed) R.string.overlay_undo_archived else R.string.overlay_undo_completed, undo.item.title),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(hud.accent)
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 16.dp)
                    .testTag(TAG_UNDO),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.overlay_undo), color = JuiceColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(left.value).height(3.dp).background(hud.accent))
    }
}

/** The tier and fill percent, e.g. "HYDRATED: 37%", above the centre card. */
@Composable
private fun HydrationLabel(hud: Hud, modifier: Modifier = Modifier) {
    val label = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f))
    Text(
        stringResource(R.string.overlay_thirst, hud.tier.name, Math.round(hud.fill * 100)),
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
        style = LocalTextStyle.current.merge(label),
        modifier = modifier.testTag(TAG_HYDRATION),
    )
}

/** The multiplier pill; it pulses while Satiated. */
@Composable
private fun MultiplierBadge(hud: Hud) {
    val satiated = hud.tier == Tier.SATIATED
    val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
        1f, if (satiated) 1.08f else 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .widthIn(min = 56.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (satiated) hud.accent.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f))
            .border(if (satiated) 2.dp else 1.dp, hud.accent, RoundedCornerShape(17.dp))
            .padding(horizontal = 12.dp)
            .testTag(TAG_MULTIPLIER),
        contentAlignment = Alignment.Center,
    ) {
        Text(hud.multiplierLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Tells the host whether the shell is in writing mode and lets it end that mode, for the BACK key. */
class WritingBack {
    internal var isWriting: () -> Boolean = { false }
    internal var leave: () -> Unit = {}
    internal var isRecapOpen: () -> Boolean = { false }
    internal var closeRecap: () -> Unit = {}
    internal var isRoutinesOpen: () -> Boolean = { false }
    internal var closeRoutines: () -> Unit = {}

    /** BACK closes the Recap or the Routines view while one is open, then ends writing mode (keeping the draft), and otherwise calls [close]. */
    fun back(close: () -> Unit) = when {
        isRecapOpen() -> closeRecap()
        isRoutinesOpen() -> closeRoutines()
        isWriting() -> leave()
        else -> close()
    }
}

/** A still liquid, for previews and tests; the overlay passes the AGSL reservoir instead. */
@Composable
fun PlainReservoir(hud: Hud, modifier: Modifier) {
    Canvas(modifier) {
        val top = size.height * (1f - hud.fill * 0.9f)
        drawRect(Brush.verticalGradient(listOf(hud.accent, hud.deep), startY = top, endY = size.height), Offset(0f, top))
    }
}

@Composable
private fun Capture(
    hud: Hud,
    writing: Boolean,
    onWritingChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier,
    onSubmit: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    fun submit() {
        if (draft.isBlank()) return
        onSubmit(draft)
        draft = ""
        onLeave()
    }
    val shape = RoundedCornerShape(if (writing) 24.dp else 28.dp)
    Row(
        modifier.fillMaxWidth().then(if (writing) Modifier else Modifier.height(56.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = !writing,
            textStyle = LocalTextStyle.current.copy(fontSize = if (writing) 20.sp else 15.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = if (writing) ImeAction.Default else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { onWritingChange(it.isFocused) }
                .testTag(TAG_CAPTURE_FIELD),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(JuiceColors.white(0.14f))
                        .border(1.dp, JuiceColors.white(0.25f), shape)
                        .padding(horizontal = 20.dp, vertical = if (writing) 18.dp else 0.dp),
                    contentAlignment = if (writing) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) Text(stringResource(R.string.overlay_capture_hint), fontSize = if (writing) 20.sp else 15.sp, color = JuiceColors.white(0.6f))
                    field()
                }
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (writing) {
                Spacer(Modifier.weight(1f))
            }
        val label = stringResource(R.string.overlay_add)
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(hud.accent)
                .clickable { submit() }
                .semantics { contentDescription = label }
                .testTag(TAG_ADD),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val w = 2.6.dp.toPx()
                val u = size.width / 24f
                drawPath(
                    Path().apply { moveTo(12 * u, 5 * u); lineTo(12 * u, 19 * u); moveTo(5 * u, 12 * u); lineTo(19 * u, 12 * u) },
                    JuiceColors.Ink,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        }
    }
}

@Composable
private fun Banner(effects: OverlayEffects, hud: Hud, modifier: Modifier) {
    val banner = effects.banner
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(BANNER_MILLIS)
            effects.clearBanner(banner.first)
        }
    }
    AnimatedVisibility(banner != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        var last by remember { mutableStateOf("") }
        if (banner != null) last = banner.second
        Box(
            Modifier
                .width(310.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(hud.accent)
                .testTag(TAG_BANNER),
            contentAlignment = Alignment.Center,
        ) {
            Text(last, color = JuiceColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** "+250 x2.4" rising and fading over the card. */
@Composable
private fun Floats(effects: OverlayEffects, hud: Hud, modifier: Modifier) {
    Box(modifier) {
        effects.floats.forEach { float ->
            val progress = remember(float.id) { Animatable(0f) }
            LaunchedEffect(float.id) {
                progress.animateTo(1f, tween(FLOAT_MILLIS, easing = LinearEasing))
                effects.clearFloat(float.id)
            }
            Text(
                float.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = hud.accent,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.merge(TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 12f))),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 146.dp - 60.dp * progress.value)
                    .graphicsLayer { alpha = ((1f - progress.value) * 2f).coerceIn(0f, 1f) },
            )
        }
    }
}
