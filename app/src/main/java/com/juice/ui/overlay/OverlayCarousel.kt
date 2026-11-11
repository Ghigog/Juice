package com.juice.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.core.model.Item
import com.juice.feedback.FeedbackEvent
import kotlin.math.abs
import kotlinx.coroutines.launch

/** The left column: cards scale up near the focal dot and snap to it. Tap a card to bring it in. */
@Composable
internal fun Carousel(
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
    var carouselRoot by remember { mutableStateOf(Offset.Zero) }
    val listStartPx = with(LocalDensity.current) { CAROUSEL_LIST_START.toPx() }
    SideEffect {
        cardPops.visibleCentres = {
            val info = listState.layoutInfo
            val x = carouselRoot.x + listStartPx + info.viewportSize.width / 2f
            info.visibleItemsInfo.map { Offset(x, carouselRoot.y + it.offset + it.size / 2f) }
        }
    }
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

            // Pre, not post: it fires even when the list is already at its first or last card.
            override suspend fun onPreFling(available: Velocity): Velocity {
                spinNow.kick(spinForFling(available.y))
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
            .onGloballyPositioned { carouselRoot = it.positionInRoot() }
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
                    val committed = state.commitTitleEdit()
                    if (!committed && !tapIsInCarouselGap(tap.y, spans)) actions.onClose()
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
            modifier = Modifier.fillMaxSize().nestedScroll(spinConnection).padding(start = CAROUSEL_LIST_START),
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

/** The carousel's cards popping away on close: which of them are on screen, top to bottom, and how far the popping has run. */
internal class CardPops {
    var visibleIndices: () -> List<Int> = { emptyList() }
    var popping by mutableStateOf<List<Int>>(emptyList())

    /** The visible cards' centres in root coordinates, top to bottom; read once when closing starts. */
    var visibleCentres: () -> List<Offset> = { emptyList() }

    /** The popping cards' centres in the shell's drawing coordinates, taken when closing started. */
    var centres by mutableStateOf<List<Offset>>(emptyList())
    val elapsed = Animatable(0f)

    /** The capture field's bounds in root coordinates, kept current by its layout. */
    var captureBounds: Rect? = null

    /** Where the froth bursts, in the shell's drawing coordinates, taken when closing started. */
    var frothAt by mutableStateOf<List<Offset>>(emptyList())

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
