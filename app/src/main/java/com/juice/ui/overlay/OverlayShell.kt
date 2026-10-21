package com.juice.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.juice.settings.JuiceSettings
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import kotlin.math.roundToInt
import com.juice.ui.Tier
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TAG_CAPTURE_FIELD = "capture_field"
const val TAG_ADD = "add_button"
const val TAG_CENTRE_CARD = "centre_card"
const val TAG_UNDO = "undo"
const val TAG_COMPLETE = "complete_button"
const val TAG_DISMISS = "dismiss_button"
const val TAG_TITLE_FIELD = "title_field"
const val TAG_TYPE_PILL = "type_pill"
const val TAG_CLOSE = "close_handle"
const val TAG_SETTINGS = "settings_button"
const val TAG_SETTINGS_DONE = "settings_done"
const val TAG_FLAVOUR_PREFIX = "flavour_"
const val TAG_SWIPE_DISTANCE_PREFIX = "swipe_distance_"
const val TAG_BANNER = "banner"
const val MAX_CARD_SCALE = 1.15f

private const val CAROUSEL_ITEM_DP = 56
private const val CAROUSEL_GAP_DP = 8
private const val BANNER_MILLIS = 2200L
private const val FLOAT_MILLIS = 1300
private const val RING_PREVIEW_MILLIS = 1400L
private const val SLICE_DP = 56

/** Scale of a card whose centre is [distance] px from the focal dot, falling off over [reach] px. */
internal fun focalScale(distance: Float, reach: Float): Float =
    1f + (MAX_CARD_SCALE - 1f) * (1f - (abs(distance) / reach).coerceIn(0f, 1f))

/**
 * The overlay: header, snapping carousel, the centre card where ✓/✗ are drawn, the undo prompt,
 * the Thirst Reservoir and the capture field, plus its settings sheet (design doc §5.1).
 * [reservoir] draws the liquid; the default is a plain fill for tests and previews.
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
) {
    val barTop = systemBars.calculateTopPadding()
    val barBottom = systemBars.calculateBottomPadding()
    // The keyboard is the one inset still followed live; the content sits above whichever is taller.
    val contentBottom = maxOf(barBottom, WindowInsets.ime.asPaddingValues().calculateBottomPadding())
    var settingsOpen by remember { mutableStateOf(false) }
    var centre by remember { mutableStateOf(0) }
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
                .fillMaxSize()
                .background(overlayTint(blurred)),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 14.dp, top = barTop + 38.dp, bottom = contentBottom + 16.dp),
            ) {
                Header(hud, onSettings = { settingsOpen = true })
                Spacer(Modifier.height(12.dp))
                val list = state.items
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Carousel(list, hud, actions, onCentre = { centre = it }, modifier = Modifier.width(112.dp).fillMaxHeight())
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CentreCard(
                            state = state,
                            item = list.getOrNull(centre.coerceAtMost(list.lastIndex)),
                            hud = hud,
                            tapButtons = settings.tapButtons,
                            reduceMotion = settings.reduceMotion,
                            onDeviceOnly = settings.onDeviceOnly,
                            actions = actions,
                            effects = effects,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Floats(effects, hud, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(44.dp)) {
                    if (undo != null) UndoToast(undo, hud) {
                        state.undoLast()
                        actions.feedback(FeedbackEvent.UNDO)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Reservoir(hud, reservoir)
                Spacer(Modifier.height(12.dp))
                Capture(hud) { text ->
                    if (state.capture(text) != null) actions.feedback(FeedbackEvent.CAPTURE)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = barTop + 120.dp)
                        .padding(bottom = barBottom),
                )
            }
            // The same slice that opened Juice, where it was left; a tap closes.
            SliceButton(
                settings, hud,
                slices = SliceLogoPainter.sliceCount(state.items.size),
                active = centre.coerceAtMost(state.items.lastIndex),
                showRing = ringPreview > 0,
                onClose = actions.onClose,
            )
        }
    }
}

/**
 * The overlay's ground: the mockup's dark teal-to-indigo glass. Over a blurred, often bright
 * wallpaper it has to stay mostly opaque, or the screen behind washes it out to grey.
 */
private fun overlayTint(blurred: Boolean): Brush {
    // Thick enough that, if the system drops the blur for a moment, the screen behind barely shows.
    val alpha = if (blurred) 0.9f else 0.97f
    return Brush.linearGradient(
        listOf(Color(0xFF0F2E35), Color(0xFF0B1A22), Color(0xFF1A1838)).map { it.copy(alpha = alpha) },
    )
}

/** The slice handle inside the overlay, at the handle's screen position; [showRing] previews the swipe distance. */
@Composable
private fun SliceButton(settings: JuiceSettings, hud: Hud, slices: Int, active: Int, showRing: Boolean, onClose: () -> Unit) {
    val label = stringResource(R.string.overlay_close)
    val painter = remember { SliceLogoPainter() }
    val style = SliceStyle.of(hud.flavour)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cx = constraints.maxWidth * settings.handleX.coerceIn(0f, 1f)
        val cy = constraints.maxHeight * settings.handleY.coerceIn(0f, 1f)
        if (showRing) {
            val radius = with(density) { settings.swipeDistance.dp.dp.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { painter.drawRing(it.nativeCanvas, cx, cy, radius, 0f, false, hud.accent.toArgb(), density.density) }
            }
        }
        val half = with(density) { (SLICE_DP / 2).dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
                .size(SLICE_DP.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .semantics { contentDescription = label }
                .testTag(TAG_CLOSE),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { painter.draw(it.nativeCanvas, size.width / 2, size.height / 2, size.width, style, slices, active) }
            }
        }
    }
}

@Composable
private fun Header(hud: Hud, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(String.format("%,d", hud.juice), fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text(stringResource(R.string.overlay_juice_label), fontSize = 11.sp, letterSpacing = 1.sp, color = JuiceColors.white(0.75f))
        }
        Spacer(Modifier.width(12.dp))
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
private fun Carousel(items: List<Item>, hud: Hud, actions: OverlayActions, onCentre: (Int) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
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
    BoxWithConstraints(modifier) {
        val pad = (maxHeight - CAROUSEL_ITEM_DP.dp) / 2
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(top = pad, bottom = pad),
            verticalArrangement = Arrangement.spacedBy(CAROUSEL_GAP_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(start = 12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val isCentre = index == centreIndex
                Column(
                    Modifier
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
                        }
                        .clip(RoundedCornerShape(16.dp))
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
private fun UndoToast(undo: Undo, hud: Hud, onUndo: () -> Unit) {
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

@Composable
private fun Reservoir(hud: Hud, reservoir: @Composable (Modifier) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, JuiceColors.white(0.2f), RoundedCornerShape(30.dp)),
    ) {
        reservoir(Modifier.fillMaxSize())
        val label = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f))
        Text(
            stringResource(R.string.overlay_thirst, hud.tier.name, Math.round(hud.fill * 100)),
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.merge(label),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp),
        )
        val satiated = hud.tier == Tier.SATIATED
        val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
            1f, if (satiated) 1.08f else 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 14.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .widthIn(min = 56.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (satiated) hud.accent.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f))
                .border(if (satiated) 2.dp else 1.dp, hud.accent, RoundedCornerShape(17.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(hud.multiplierLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
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
private fun Capture(hud: Hud, onSubmit: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    fun submit() {
        if (draft.isBlank()) return
        onSubmit(draft)
        draft = ""
    }
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).fillMaxHeight().testTag(TAG_CAPTURE_FIELD),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(JuiceColors.white(0.14f))
                        .border(1.dp, JuiceColors.white(0.25f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) Text(stringResource(R.string.overlay_capture_hint), fontSize = 15.sp, color = JuiceColors.white(0.6f))
                    field()
                }
            },
        )
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
