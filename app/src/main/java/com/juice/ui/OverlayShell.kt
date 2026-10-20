package com.juice.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import com.juice.core.gesture.GesturePoint
import com.juice.core.gesture.GestureRecognizer
import com.juice.core.gesture.GestureResult
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

const val UNDO_WINDOW_MILLIS = 10_000L
private const val MAX_SCALE = 1.15f
private const val SHAKE_PX = 24f

/** An item just completed (✓) or dismissed (✗), kept so it can be put back. */
private class Undoable(val item: Item, val index: Int)

/**
 * The overlay: capture field, snapping carousel of active items, and ✓/✗ drawing on the centre card.
 * Changes show at once and are saved to [repository] in the background.
 */
@Composable
fun OverlayShell(
    repository: ItemRepository,
    initialItems: List<Item> = emptyList(),
    modifier: Modifier = Modifier,
    recogniser: GestureRecognizer = remember { GestureRecognizer() },
) {
    val items = remember { mutableStateListOf<Item>().apply { addAll(initialItems) } }
    var undo by remember { mutableStateOf<Undoable?>(null) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun capture() {
        val title = draft.trim()
        if (title.isEmpty()) return
        val note = Item(UUID.randomUUID().toString(), ItemType.Note, title)
        items.add(note)
        draft = ""
        scope.launch { repository.save(note) }
    }

    fun finish(item: Item, status: ItemStatus) {
        undo = Undoable(item, items.indexOf(item))
        items.remove(item)
        scope.launch { repository.save(item.copy(status = status)) }
    }

    fun restore(u: Undoable) {
        items.add(u.index.coerceIn(0, items.size), u.item)
        undo = null
        scope.launch { repository.save(u.item) }
    }

    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_WINDOW_MILLIS)
            undo = null
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).testTag("capture-field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { capture() }),
            )
            Button(onClick = ::capture, modifier = Modifier.padding(start = 8.dp).testTag("capture-button")) {
                Text("Add")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Centre the first/last card at the focal dot with half-viewport padding.
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().testTag("carousel"),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val centre = listState.layoutInfo.let { info ->
                        info.visibleItemsInfo.minByOrNull {
                            abs(it.offset + it.size / 2 - (info.viewportStartOffset + info.viewportEndOffset) / 2)
                        }?.index
                    } == index
                    val scale = focalScale(listState, index)
                    ItemCard(
                        item = item,
                        isCentre = centre,
                        recogniser = recogniser,
                        onComplete = { finish(item, ItemStatus.Completed) },
                        onDismiss = { finish(item, ItemStatus.Archived) },
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).width(8.dp).height(8.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Gray),
            )
        }

        undo?.let { u ->
            Row(Modifier.fillMaxWidth().testTag("undo-bar"), verticalAlignment = Alignment.CenterVertically) {
                Text("“${u.item.title}” done", Modifier.weight(1f))
                TextButton(onClick = { restore(u) }, modifier = Modifier.testTag("undo")) { Text("Undo") }
            }
        }
    }
}

/** 1.0 at the edges, [MAX_SCALE] when the card is centred on the focal dot. */
private fun focalScale(state: androidx.compose.foundation.lazy.LazyListState, index: Int): Float {
    val info = state.layoutInfo
    val card = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 1f
    val focal = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val distance = abs(card.offset + card.size / 2f - focal)
    val closeness = (1f - distance / card.size).coerceIn(0f, 1f)
    return 1f + (MAX_SCALE - 1f) * closeness
}

@Composable
private fun ItemCard(
    item: Item,
    isCentre: Boolean,
    recogniser: GestureRecognizer,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val stroke = remember { mutableListOf<GesturePoint>() }
    Box(
        modifier
            .width(240.dp).height(160.dp)
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEEEEEE))
            .testTag("card-${item.title}")
            // Drawing is only read inside the centre card; other cards leave touches to the carousel.
            .then(
                if (!isCentre) Modifier else Modifier.pointerInput(item.id) {
                    detectDragGestures(
                        onDragStart = { stroke.clear(); stroke.add(GesturePoint(it.x.toDouble(), it.y.toDouble())) },
                        onDrag = { change, _ ->
                            change.consume()
                            stroke.add(GesturePoint(change.position.x.toDouble(), change.position.y.toDouble()))
                        },
                        onDragEnd = {
                            val result = recogniser.recognise(stroke.toList())
                            stroke.clear()
                            when (result) {
                                is GestureResult.Checkmark -> onComplete()
                                is GestureResult.Dismiss -> onDismiss()
                                GestureResult.NotRecognised -> scope.launch {
                                    for (x in listOf(SHAKE_PX, -SHAKE_PX, SHAKE_PX / 2, 0f)) shake.animateTo(x, tween(50))
                                }
                            }
                        },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(item.title)
    }
}
