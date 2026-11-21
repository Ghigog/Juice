package com.juice.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.juice.R
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TAG_CATEGORY_CAPSULE = "category_capsule"
const val TAG_CATEGORY_DOT_PREFIX = "category_dot_"
const val TAG_SHELF_CAPSULE_PREFIX = "shelf_capsule_"

/** One category on the shelf; Main (and anything else that must stay put) is not [movable]. */
internal data class ShelfCategory(val name: String, val movable: Boolean)

private val SHELF_GAP = 8.dp

/**
 * The current category's name in a glass capsule, with one dot per category beside it. Tapping the capsule renames;
 * a long tap unfurls the strip into a shelf of capsules, one per category, and lifts the pressed one under the finger
 * so it can slide along the shelf. Main stays first. The shelf folds back after a pause or a tap outside it.
 * Callbacks only: the caller owns the order.
 */
@Composable
internal fun CategoryStrip(
    order: List<ShelfCategory>,
    current: Int,
    hud: Hud,
    reduceMotion: Boolean,
    onCategory: () -> Unit,
    onMoveCategory: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveEarlier: (index: Int) -> Unit,
    onMoveLater: (index: Int) -> Unit,
    onShelfTick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOrder by rememberUpdatedState(order)
    val latestCurrent by rememberUpdatedState(current)
    val latestOnCategory by rememberUpdatedState(onCategory)
    val latestOnMove by rememberUpdatedState(onMoveCategory)
    val latestOnEarlier by rememberUpdatedState(onMoveEarlier)
    val latestOnLater by rememberUpdatedState(onMoveLater)
    val latestOnTick by rememberUpdatedState(onShelfTick)
    val latestReduce by rememberUpdatedState(reduceMotion)
    val earlier = stringResource(R.string.shelf_move_earlier)
    val later = stringResource(R.string.shelf_move_later)
    val density = androidx.compose.ui.platform.LocalDensity.current

    var open by remember { mutableStateOf(false) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var slot by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var nudgeTarget by remember { mutableFloatStateOf(0f) }
    var dropStamp by remember { mutableIntStateOf(0) }
    val settle = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val rests = remember { HashMap<Int, Rect>() }
    var containerLeft by remember { mutableFloatStateOf(0f) }
    val gapPx = with(density) { SHELF_GAP.toPx() }
    val nudgePx = with(density) { SHELF_NUDGE_DP.dp.toPx() }

    val busy = pressedIndex >= 0 || dragFrom >= 0
    // The pause after the last drop (driven by the frame clock, so a test advances it with mainClock).
    LaunchedEffect(dropStamp, busy) {
        if (dropStamp > 0 && !busy && open) {
            delay(SHELF_FOLD_MILLIS)
            open = false
        }
    }

    fun nearest(x: Float, fallback: Int): Int =
        rests.entries.minByOrNull { abs(it.value.center.x - x) }?.key ?: fallback

    /** Where the lifted capsule rests once dropped in [to], relative to where it started. */
    fun settleOffset(from: Int, to: Int): Float {
        val a = rests[from] ?: return 0f
        val b = rests[to] ?: return 0f
        return if (to > from) b.right - a.right else b.left - a.left
    }

    /** How far capsule [i] slides aside while [dragFrom] is hovering over [slot]. */
    fun neighbourOffset(i: Int): Float {
        val from = dragFrom
        if (from < 0 || i == from) return 0f
        val width = rests[from]?.width ?: return 0f
        return when {
            slot > from && i in (from + 1)..slot -> -(width + gapPx)
            slot < from && i in slot until from -> width + gapPx
            else -> 0f
        }
    }

    val nudge by animateFloatAsState(
        if (reduceMotion) 0f else nudgeTarget,
        if (reduceMotion) snap() else categoryNudgeBack(),
        label = "shelfNudge",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .height(MinTouchTarget)
            .pointerInput(open) { if (open) detectTapGestures { open = false } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(MinTouchTarget)
                .onGloballyPositioned { containerLeft = it.boundsInRoot().left }
                .testTag(TAG_CATEGORY_CAPSULE)
                .semantics(mergeDescendants = !open) {
                    if (!open) {
                        role = Role.Button
                        onClick { latestOnCategory(); true }
                        onLongClick { open = true; dropStamp++; true }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val pressed = if (open) nearest(down.position.x, latestCurrent) else latestCurrent
                        val movable = latestOrder.getOrNull(pressed)?.movable == true
                        pressedIndex = pressed
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            pressedIndex = -1
                            if (up != null && !up.pressed) {
                                if (open) dropStamp++ else latestOnCategory()
                            }
                            return@awaitEachGesture
                        }
                        open = true
                        fingerX = down.position.x
                        if (movable) {
                            dragFrom = pressed
                            slot = pressed
                            dragging = true
                        }
                        drag(down.id) { change ->
                            change.consume()
                            val x = change.position.x
                            fingerX = x
                            val raw = nearest(x, pressed)
                            if (movable) {
                                val target = maxOf(raw, 1)
                                if (target != slot) {
                                    slot = target
                                    latestOnTick()
                                }
                                // Pushing against Main: it gives a little and springs back.
                                nudgeTarget = if (raw < 1) -nudgePx else 0f
                            } else {
                                nudgeTarget = ((x - down.position.x) * 0.25f).coerceIn(-nudgePx, nudgePx)
                            }
                        }
                        // Released (or cancelled).
                        nudgeTarget = 0f
                        pressedIndex = -1
                        val from = dragFrom
                        val to = slot
                        if (movable && from >= 0) {
                            val live = fingerX - (rests[from]?.center?.x ?: fingerX)
                            scope.launch {
                                if (!latestReduce) {
                                    settle.snapTo(live)
                                    dragging = false
                                    settle.animateTo(settleOffset(from, to), shelfSlide())
                                } else {
                                    dragging = false
                                }
                                if (to != from) {
                                    latestOnMove(from, to)
                                    glow.snapTo(1f)
                                    scope.launch { glow.animateTo(0f, tween(SHELF_GLOW_MILLIS)) }
                                }
                                dragFrom = -1
                                slot = -1
                                dropStamp++
                            }
                        } else {
                            dropStamp++
                        }
                    }
                },
        ) {
            Row(Modifier, horizontalArrangement = Arrangement.spacedBy(SHELF_GAP), verticalAlignment = Alignment.CenterVertically) {
                order.forEachIndexed { i, category ->
                    val visible = open || i == current
                    AnimatedVisibility(
                        visible = visible,
                        enter = if (reduceMotion) fadeIn(tween(CATEGORY_FADE_MILLIS)) else fadeIn() + expandHorizontally(spring(SHELF_WOBBLE_DAMPING, Spring.StiffnessMediumLow)),
                        exit = if (reduceMotion) fadeOut(tween(CATEGORY_FADE_MILLIS)) else fadeOut() + shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)),
                    ) {
                        val lifted = i == dragFrom
                        val pressedNow = i == pressedIndex
                        val slide by animateFloatAsState(
                            if (reduceMotion) 0f else neighbourOffset(i),
                            if (reduceMotion) snap() else shelfSlide(),
                            label = "shelfSlide",
                        )
                        val swapTarget = if (lifted) settleOffset(i, slot) else neighbourOffset(i)
                        val fade = remember { Animatable(1f) }
                        if (reduceMotion) {
                            LaunchedEffect(swapTarget) {
                                fade.snapTo(0.3f)
                                fade.animateTo(1f, tween(CATEGORY_FADE_MILLIS))
                            }
                        }
                        val squeeze by animateFloatAsState(
                            if (pressedNow && !reduceMotion && !lifted) 0.94f else 1f,
                            if (reduceMotion) snap() else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                            label = "categorySqueeze",
                        )
                        val dip = if (pressedNow && reduceMotion) 0.7f else 1f
                        Box(
                            Modifier
                                .height(MinTouchTarget)
                                .widthIn(max = 200.dp)
                                .zIndex(if (lifted) 1f else 0f)
                                .onGloballyPositioned { rests[i] = it.boundsInRoot().translate(-containerLeft, 0f) }
                                .then(
                                    if (open) {
                                        Modifier.semantics(mergeDescendants = true) {
                                            val moves = mutableListOf<CustomAccessibilityAction>()
                                            if (category.movable && i >= 2) moves += CustomAccessibilityAction(earlier) { latestOnEarlier(i); dropStamp++; true }
                                            if (category.movable && i < order.lastIndex) moves += CustomAccessibilityAction(later) { latestOnLater(i); dropStamp++; true }
                                            customActions = moves
                                        }.testTag(TAG_SHELF_CAPSULE_PREFIX + i)
                                    } else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                category.name,
                                Modifier
                                    .graphicsLayer {
                                        val live = when {
                                            lifted && reduceMotion -> 0f
                                            lifted && dragging -> fingerX - (rests[i]?.center?.x ?: fingerX)
                                            lifted -> settle.value
                                            i == 0 -> nudge
                                            else -> slide
                                        }
                                        translationX = live
                                        val swell = if (lifted && !reduceMotion) SHELF_LIFT_SCALE else squeeze
                                        scaleX = swell; scaleY = swell
                                        alpha = (if (lifted && reduceMotion) 0.7f else dip) * fade.value * (if (!category.movable && open) 0.6f else 1f)
                                        if (lifted && !reduceMotion) {
                                            shadowElevation = 12.dp.toPx()
                                            shape = JuiceShapes.Capsule
                                            clip = false
                                        }
                                    }
                                    .height(30.dp)
                                    .clip(JuiceShapes.Capsule)
                                    .background(JuiceColors.white(0.14f))
                                    .border(1.dp, JuiceColors.white(0.25f), JuiceShapes.Capsule)
                                    .padding(horizontal = 16.dp)
                                    .wrapContentHeight(Alignment.CenterVertically),
                                fontSize = JuiceType.Label,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(order.size) { i ->
                Box(
                    Modifier
                        .size(6.dp)
                        .then(
                            if (i == current) Modifier.drawBehind {
                                val g = glow.value
                                if (g > 0f) drawCircle(hud.accent.copy(alpha = 0.5f * g), radius = size.width * (0.5f + 1.2f * g))
                            } else Modifier,
                        )
                        .clip(CircleShape)
                        .background(if (i == current) hud.accent else JuiceColors.white(0.3f))
                        .semantics { selected = i == current }
                        .testTag(TAG_CATEGORY_DOT_PREFIX + i),
                )
            }
        }
    }
}
