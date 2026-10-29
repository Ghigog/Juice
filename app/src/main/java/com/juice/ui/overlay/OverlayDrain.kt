package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.core.model.Item

const val TAG_DRAIN_GHOST = "drain_ghost"
const val DRAIN_MILLIS = 600
const val DRAIN_END_SCALE = 0.1f
const val DRAIN_END_ROTATION = 25f

/** Where the draining ghost is at one moment: [offset] of its centre, its [scale], [rotation] in degrees and [alpha]. */
data class DrainFrame(val offset: Offset, val scale: Float, val rotation: Float, val alpha: Float)

/**
 * The ghost's pose at [fraction] (0..1 of the drain) as it goes from the [start] rect to the [end] rect.
 * Time is eased in so it speeds up like a drain; the path bows sideways; it fades over the last quarter.
 */
fun drainFrame(start: Rect, end: Rect, fraction: Float): DrainFrame {
    val f = fraction.coerceIn(0f, 1f)
    val t = f * f * f
    val from = start.center
    val to = end.center
    val dx = to.x - from.x
    val dy = to.y - from.y
    // Bows the straight line sideways by a fifth of its length, most in the middle.
    val bow = 4f * t * (1f - t) * 0.2f
    val offset = Offset(from.x + dx * t - dy * bow, from.y + dy * t + dx * bow)
    val scale = 1f + (DRAIN_END_SCALE - 1f) * t
    val alpha = if (f < 0.75f) 1f else 1f - (f - 0.75f) / 0.25f
    return DrainFrame(offset, scale, DRAIN_END_ROTATION * t, alpha)
}

/**
 * A ghost of [item] that shrinks, turns and fades from [from] (the centre card) into [to] (the Routines button),
 * then calls [onDone]. Drawn over the card row by the shell.
 */
@Composable
internal fun OverlayDrain(item: Item, from: Rect, to: Rect, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) {
        progress.animateTo(1f, tween(DRAIN_MILLIS, easing = LinearEasing))
        onDone()
    }
    val density = LocalDensity.current
    val frame = drainFrame(from, to, progress.value)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(with(density) { from.width.toDp() }, with(density) { from.height.toDp() })
                .graphicsLayer {
                    translationX = frame.offset.x - from.width / 2f
                    translationY = frame.offset.y - from.height / 2f
                    scaleX = frame.scale
                    scaleY = frame.scale
                    rotationZ = frame.rotation
                    alpha = frame.alpha
                }
                .background(JuiceColors.of(item.type).copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                .testTag(TAG_DRAIN_GHOST),
        ) {
            Text(item.title, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(16.dp))
        }
    }
}
