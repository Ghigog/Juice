package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.juice.core.gesture.GesturePoint
import com.juice.core.gesture.GestureRecognizer
import com.juice.core.gesture.GestureResult
import com.juice.core.model.Item
import kotlin.math.abs
import kotlinx.coroutines.delay

const val TAG_CAPTURE_FIELD = "capture_field"
const val TAG_CENTRE_CARD = "centre_card"
const val TAG_UNDO = "undo"
const val MAX_CARD_SCALE = 1.15f

/** Scale of a card whose centre is [distance] px from the focal dot, falling off over [reach] px. */
internal fun focalScale(distance: Float, reach: Float): Float =
    1f + (MAX_CARD_SCALE - 1f) * (1f - (abs(distance) / reach).coerceIn(0f, 1f))

/** The overlay: capture field, snapping carousel, ✓/✗ drawn on the centre card, and the undo prompt. */
@Composable
fun OverlayShell(state: OverlayShellState, modifier: Modifier = Modifier) {
    val recognizer = remember { GestureRecognizer() }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val centreIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index
        }
    }
    val undo = state.undo
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(OverlayShellState.UNDO_WINDOW_MILLIS)
            state.expireUndo(undo)
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                state.capture(draft)
                draft = ""
            }),
            modifier = Modifier.fillMaxWidth().testTag(TAG_CAPTURE_FIELD),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    val isCentre = index == centreIndex
                    ItemCard(
                        item = item,
                        isCentre = isCentre,
                        scale = {
                            val info = listState.layoutInfo
                            val own = info.visibleItemsInfo.firstOrNull { it.index == index }
                            if (own == null) 1f else {
                                val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                focalScale(own.offset + own.size / 2f - middle, own.size.toFloat())
                            }
                        },
                        recognise = { stroke -> recognizer.recognise(stroke) },
                        onComplete = { state.complete(item) },
                        onDismiss = { state.dismiss(item) },
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).size(8.dp).background(Color.White, CircleShape))
        }
        if (undo != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (undo.dismissed) "Dismissed \"${undo.item.title}\"" else "Completed \"${undo.item.title}\"",
                    color = Color.White,
                )
                TextButton(onClick = state::undoLast, modifier = Modifier.testTag(TAG_UNDO)) { Text("Undo") }
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: Item,
    isCentre: Boolean,
    scale: () -> Float,
    recognise: (List<GesturePoint>) -> GestureResult,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shake = remember { Animatable(0f) }
    var result by remember { mutableStateOf<GestureResult?>(null) }
    LaunchedEffect(result) {
        when (result) {
            is GestureResult.Checkmark -> onComplete()
            is GestureResult.Dismiss -> onDismiss()
            GestureResult.NotRecognised -> shake.animateTo(0f, keyframes {
                durationMillis = 300
                -12f at 50; 12f at 100; -8f at 150; 8f at 200; -4f at 250
            })
            null -> Unit
        }
        result = null
    }
    // Drawing is only handled on the centre card, and only inside its bounds.
    val drawing = if (isCentre) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                    val points = mutableListOf(GesturePoint(down.position.x.toDouble(), down.position.y.toDouble()))
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        change.consume()
                        if (!change.pressed) break
                        points += GesturePoint(change.position.x.toDouble(), change.position.y.toDouble())
                    }
                    result = recognise(points)
                }
            }
        }
    } else Modifier
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .size(width = 220.dp, height = 300.dp)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                translationX = shake.value
            }
            .clipToBounds()
            .then(if (isCentre) Modifier.testTag(TAG_CENTRE_CARD) else Modifier)
            .then(drawing),
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(item.title)
        }
    }
}
