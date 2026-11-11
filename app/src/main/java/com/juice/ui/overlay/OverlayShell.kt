package com.juice.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.juice.R
import com.juice.feedback.FeedbackEvent
import com.juice.settings.CharacterSettings
import com.juice.settings.JuiceSettings
import com.juice.ui.logo.SliceLogoPainter
import java.time.Clock
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
const val TAG_CARD_TITLE = "card_title"
const val TAG_TITLE_FIELD = "title_field"
const val TAG_TYPE_PILL = "type_pill"

/** Prefix of the type menu entries; the item type name follows. */
const val TAG_TYPE_OPTION_PREFIX = "type_option_"
const val TAG_CLOSE = "close_handle"
const val TAG_CAROUSEL = "carousel"
const val TAG_RECAP = "recap_button"
const val TAG_ROUTINES = "routines_button"
const val TAG_SETTINGS = "settings_button"
const val TAG_SETTINGS_DONE = "settings_done"
const val TAG_FLAVOUR_PREFIX = "flavour_"
const val TAG_SWIPE_DISTANCE_PREFIX = "swipe_distance_"
const val TAG_LIQUID_QUALITY_PREFIX = "liquid_quality_"
const val TAG_BANNER = "banner"
const val MAX_CARD_SCALE = 1.15f

/** True when [y] lies between the first visible item's start and the last one's end but on no item: a gap between cards. */
internal fun tapIsInCarouselGap(y: Float, itemSpans: List<IntRange>): Boolean {
    if (itemSpans.isEmpty()) return false
    val first = itemSpans.minOf { it.first }
    val last = itemSpans.maxOf { it.last }
    return y > first && y < last && itemSpans.none { y >= it.first && y <= it.last }
}

internal const val CAROUSEL_ITEM_DP = 56
internal const val CAROUSEL_GAP_DP = 8
internal const val BANNER_MILLIS = 2200L
/** A longer banner, such as why a coach could not be reached, stays up long enough to read. */
internal const val BANNER_MILLIS_PER_CHAR = 60L
internal const val FLOAT_MILLIS = 1300
internal const val RING_PREVIEW_MILLIS = 1400L
internal const val BACKDROP_MAX_DP = 280f

/** Below this height (a phone on its side) the overlay tightens its gaps and floats the undo prompt over the cards. */
internal val COMPACT_HEIGHT = 480.dp

/**
 * The backdrop slice's size, in dp, on a screen [screenHeightDp] tall: 280dp, but never more than half
 * the height, so on a phone on its side it stays in the corner instead of covering the header.
 */
internal fun backdropSliceDp(screenHeightDp: Float): Float = minOf(BACKDROP_MAX_DP, screenHeightDp / 2)
internal const val WIGGLE_MILLIS = 110
internal const val WIGGLE_DEGREES = 1.5f
internal const val WIGGLE_DP = 1.5f
internal const val HELD_ALPHA = 0.4f

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
    /** Whether a Groq key is saved; without one Groq is never used, so the parsing chip says on device. */
    groqKeySaved: Boolean = false,
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
    /** Whether the backdrop slice is drawn; the host hides it while the handle's stage window draws the slice instead. */
    sliceShown: Boolean = true,
    /** The backdrop slice's opening spin in degrees per second (sign = direction), from the pull that opened the overlay. */
    initialSpin: Float = 0f,
    /** The backdrop slice's opening angle in degrees, so it carries on from the handle slice's angle. */
    initialAngle: Float = 0f,
    /** Called when closing starts with where the froth and the per-card pops will burst, in the shell's drawing coordinates. */
    onPopsPlanned: (froth: List<Offset>, cards: List<Offset>) -> Unit = { _, _ -> },
) {
    val barTop = systemBars.calculateTopPadding()
    val barBottom = systemBars.calculateBottomPadding()
    // The keyboard is the one inset still followed live; the content sits above whichever is taller.
    val contentBottom = maxOf(barBottom, WindowInsets.ime.asPaddingValues().calculateBottomPadding())
    var settingsOpen by remember { mutableStateOf(false) }
    var recapOpen by remember { mutableStateOf(false) }
    var routinesOpen by remember { mutableStateOf(false) }
    // Held here so the draft survives Capture leaving composition while Routines is open.
    var captureDraft by remember { mutableStateOf("") }
    var centre by remember { mutableStateOf(0) }
    // Where the centre card and the Routines button are, so a Routine not due today can drain from one into the other.
    var juiceBoxBounds by remember { mutableStateOf<Rect?>(null) }
    var routinesBounds by remember { mutableStateOf<Rect?>(null) }
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
    val spin = remember(settings.reduceMotion) { SpinMomentum(enabled = !settings.reduceMotion).apply { seed(initialAngle); kick(initialSpin) } }
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
    val currentOnPopsPlanned by rememberUpdatedState(onPopsPlanned)
    val cardPops = remember { CardPops() }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    // Cancelled with the composition, so tearing the overlay down mid-close never calls onClosed.
    LaunchedEffect(closing) {
        if (closing) {
            if (!settings.reduceMotion) {
                // The visible cards pop top to bottom, then the centre card and the capture field, each with its sound;
                // the froth (the shared pop) starts with the centre card.
                val bounds = Rect(Offset.Zero, Size(contentSize.width.toFloat(), contentSize.height.toFloat()))
                val onScreen = cardPops.visibleIndices().zip(cardPops.visibleCentres().map { it - canvasOrigin })
                    .filter { (_, centre) -> bounds.contains(centre) }
                val visible = onScreen.map { it.first }
                cardPops.centres = onScreen.map { it.second }
                cardPops.popping = visible
                cardPops.frothAt = frothOrigins(
                    juiceBoxBounds?.center?.minus(canvasOrigin), cardPops.captureBounds?.center?.minus(canvasOrigin), bounds,
                )
                currentOnPopsPlanned(cardPops.frothAt, cardPops.centres)
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
                reveal.animateTo(0f, tween(CLOSE_DRAIN_MILLIS, easing = FastOutSlowInEasing))
            }
            currentOnClosed()
        }
    }
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
        BoxWithConstraints(
            modifier
                .fillMaxSize(),
        ) {
            val compact = maxHeight < COMPACT_HEIGHT
            val layoutDirection = LocalLayoutDirection.current
            val barStart = systemBars.calculateStartPadding(layoutDirection)
            val barEnd = systemBars.calculateEndPadding(layoutDirection)
            val topGap = if (compact) 10.dp else 38.dp
            // The content keeps clear of the bars and cutout on every side; in landscape they sit at the sides.
            val content = PaddingValues(
                start = barStart + 14.dp,
                end = barEnd + 14.dp,
                top = barTop + topGap,
                bottom = contentBottom + if (compact) 10.dp else 16.dp,
            )
            SliceBackdrop(
                hud,
                slices = SliceLogoPainter.sliceCount(state.items.size),
                active = centre.coerceAtMost(state.items.lastIndex),
                spin = spin,
                shown = sliceShown,
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
                    .pointerInput(Unit) { detectTapGestures { if (!state.commitTitleEdit()) actions.onClose() } }
                    .semantics { contentDescription = closeLabel }
                    .testTag(TAG_CLOSE),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { canvasOrigin = it.positionInRoot() }
                    .onSizeChanged { contentSize = it },
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    // Under the coach chat the main view stays composed (keeping the carousel position and writing mode) but invisible.
                    .then(
                        if (state.coachChat?.open == true) Modifier.alpha(0f).clearAndSetSemantics {}
                        else Modifier,
                    )
                    .then(
                        // Outside the field and the plus button, a tap ends writing mode.
                        if (writing) Modifier.pointerInput(Unit) { detectTapGestures { leaveWriting() } }
                        else Modifier,
                    )
                    .padding(content),
            ) {
                // The Routines view covers the main view and hosts its own carousel; the service has one reservoir View, which only one host can hold.
                if (!writing && !routinesOpen) {
                    val undoToast = @Composable {
                        if (undo != null) UndoToast(undo, hud) {
                            state.undoLast()
                            actions.feedback(FeedbackEvent.UNDO)
                        }
                    }
                    // On a short screen the undo prompt sits in the header's empty middle rather than keeping a row of its own.
                    Header(
                        hud, entranceProgress, cardPops.element(0),
                        onRecap = { recapOpen = true }, onRoutines = { state.routines.observe(); routinesOpen = true }, onSettings = { settingsOpen = true },
                        onRoutinesBounds = { routinesBounds = it },
                        middle = if (compact) undoToast else null,
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    CardRow(
                        state, hud, settings, groqKeySaved, actions, effects, reservoir, centre,
                        onCentre = { centre = it }, modifier = Modifier.weight(1f).fillMaxWidth(),
                        entrance = entranceProgress, spin = spin, cardPops = cardPops,
                        onJuiceBoxBounds = { juiceBoxBounds = it },
                    )
                    if (!compact) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(44.dp).entrance(entranceProgress).then(cardPops.element(0))) { undoToast() }
                    }
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                }
                if (!state.editingTitle && !routinesOpen) Capture(
                    hud,
                    draft = captureDraft,
                    onDraftChange = { captureDraft = it },
                    writing = writing,
                    onWritingChange = { writing = it },
                    onLeave = ::leaveWriting,
                    modifier = (if (writing) Modifier.weight(1f) else Modifier.entrance(entranceProgress)).then(cardPops.element(1))
                        .onGloballyPositioned { cardPops.captureBounds = it.boundsInRoot() },
                ) { text ->
                    if (state.capture(text) != null) actions.feedback(FeedbackEvent.CAPTURE)
                }
            }
            state.draining?.let { ghost ->
                val from = juiceBoxBounds
                val to = routinesBounds
                if (from == null || to == null || settings.reduceMotion || routinesOpen) {
                    LaunchedEffect(ghost) { state.drainDone() }
                } else {
                    OverlayDrain(ghost, from, to, onDone = state::drainDone)
                }
            }
            val chat = state.coachChat
            if (chat != null && chat.open) {
                CoachChat(
                    chat, hud,
                    animate = !settings.reduceMotion,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(cardPops.element(1))
                        .background(overlayTint(blurred))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                        .padding(content),
                )
            }
            state.creator?.let { creator ->
                CreatorFlow(
                    creator, hud, onConfirm = state::createCharacter, onCancel = state::cancelCreator,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayTint(blurred))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                        .padding(content),
                )
            }
            val needsCloud = stringResource(R.string.coach_needs_cloud)
            val unreachable = chat?.let { stringResource(R.string.coach_unreachable, it.character.name, it.reason) }.orEmpty()
            LaunchedEffect(chat?.notice) {
                when (chat?.notice) {
                    CoachNotice.CloudRequired -> effects.banner(needsCloud)
                    CoachNotice.Unreachable -> effects.banner(unreachable)
                    null -> {}
                }
            }
            Banner(effects, hud, Modifier.align(Alignment.TopCenter).padding(top = barTop + if (compact) 64.dp else 92.dp))
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
                    groqKeySaved = groqKeySaved,
                    onDone = { settingsOpen = false },
                    onSwipeDistance = { ringPreview++ },
                    characters = characters,
                    modifier = Modifier
                        .padding(top = barTop + if (compact) 16.dp else 120.dp, start = barStart, end = barEnd)
                        // A phone-wide sheet, not a screen-wide one, when the screen is on its side.
                        .widthIn(max = SHEET_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(bottom = barBottom),
                )
            }
            if (routinesOpen) {
                RoutinesView(
                    state.routines, hud, settings, groqKeySaved, actions, effects, reservoir,
                    onClose = { routinesOpen = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayTint(blurred))
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                        .padding(content),
                )
            }
            if (recapOpen) {
                RecapScreen(
                    hud, settings.reduceMotion, clock, actions,
                    onClose = { recapOpen = false },
                    systemBars = systemBars,
                    topGap = topGap,
                )
            }
            if (ringPreview > 0) SwipeRing(settings, hud)
            }
            if (cardPops.centres.isNotEmpty()) {
                // Each card explodes where it was, drawn here because the card and the carousel clip to their own bounds.
                val explosions = remember(cardPops.centres, contentSize) {
                    val w = contentSize.width.toFloat()
                    cardPops.centres.mapIndexed { rank, centre ->
                        cardExplosionBubbles(centre, rank, maxDistance = w * 0.2f, maxRadius = w * 0.03f)
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    explosions.forEachIndexed { rank, bubbles ->
                        val p = cardPopProgress(cardPops.elapsed.value, rank)
                        if (cardExplosionVisible(p)) bubbles.forEach { bubble ->
                            val (centre, radius) = bubble.at(p)
                            drawCircle(hud.accent.copy(alpha = 0.7f), radius, centre)
                        }
                    }
                }
            }
            if (pop.value > 0f) {
                // Froth bursts from the centre card and the capture field.
                val bubbles = remember(cardPops.frothAt, contentSize) {
                    val w = contentSize.width.toFloat()
                    frothBubbles(cardPops.frothAt, FROTH_SEED, maxDistance = w * 0.35f, maxRadius = w * 0.04f)
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

internal const val FROTH_SEED = 44L

/** The settings sheet is no wider than this, so on a phone on its side it stays a sheet. */
internal val SHEET_MAX_WIDTH = 560.dp

/** The carousel's list is inset this far from its left edge. */
internal val CAROUSEL_LIST_START = 12.dp

/** The carousel beside the centre card for the items of [state]; shared by the main view and the Routines view. */
@Composable
internal fun CardRow(
    state: OverlayShellState,
    hud: Hud,
    settings: JuiceSettings,
    groqKeySaved: Boolean,
    actions: OverlayActions,
    effects: OverlayEffects,
    reservoir: @Composable (Modifier) -> Unit,
    centre: Int,
    onCentre: (Int) -> Unit,
    modifier: Modifier = Modifier,
    entrance: () -> Float = { 1f },
    spin: SpinMomentum = remember { SpinMomentum() },
    cardPops: CardPops = remember { CardPops() },
    onJuiceBoxBounds: (Rect) -> Unit = {},
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
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { onJuiceBoxBounds(it.boundsInRoot()) }.testTag(TAG_JUICE_BOX)) {
                CentreCard(
                    state = state,
                    item = list.getOrNull(centre.coerceAtMost(list.lastIndex)),
                    hud = hud,
                    tapButtons = settings.tapButtons,
                    reduceMotion = settings.reduceMotion,
                    onDeviceOnly = settings.onDeviceOnly || !groqKeySaved,
                    actions = actions,
                    effects = effects,
                    reservoir = reservoir,
                    modifier = Modifier.fillMaxSize(),
                    onEditingChange = { editing = it },
                )
                Floats(effects, hud, Modifier.fillMaxSize())
                ShakeReward(hud, settings.reduceMotion, actions, Modifier.fillMaxSize())
            }
        }
    }
}
