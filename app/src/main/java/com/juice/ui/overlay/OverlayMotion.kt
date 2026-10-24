package com.juice.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** How long the ground takes to grow from the backdrop slice to cover the screen. */
internal const val REVEAL_MILLIS = 400

/** How long one piece of content takes to rise (or fall) into place. */
internal const val ITEM_ENTRANCE_MILLIS = 350

/** Gap between carousel cards starting their rise; cards past [MAX_STAGGERED_ITEMS] start with the last one. */
internal const val ENTRANCE_STAGGER_MILLIS = 60
internal const val MAX_STAGGERED_ITEMS = 6

/** The whole content entrance, from the first card starting to the last one landing. */
internal const val ENTRANCE_TOTAL_MILLIS = ITEM_ENTRANCE_MILLIS + (MAX_STAGGERED_ITEMS - 1) * ENTRANCE_STAGGER_MILLIS

private val ENTRANCE_DISTANCE = 48.dp

/** The backdrop slice's centre lies this far past the screen's top-right corner. */
private val REVEAL_ORIGIN_OVERSHOOT = 70.dp

/** When item [index] starts its entrance, in milliseconds after the entrance begins; bounded for long lists. */
internal fun entranceDelayMillis(index: Int): Int = index.coerceIn(0, MAX_STAGGERED_ITEMS - 1) * ENTRANCE_STAGGER_MILLIS

/**
 * How far along item [index] is, 0..1, when the whole entrance is at [progress] (0..1).
 * Items start one after another, each easing out over [ITEM_ENTRANCE_MILLIS].
 */
internal fun entranceFraction(progress: Float, index: Int): Float {
    val elapsed = progress * ENTRANCE_TOTAL_MILLIS - entranceDelayMillis(index)
    return FastOutSlowInEasing.transform((elapsed / ITEM_ENTRANCE_MILLIS).coerceIn(0f, 1f))
}

/** The distance from [origin] to the farthest corner of a [width] x [height] screen: the circle that covers it. */
internal fun revealRadius(width: Float, height: Float, origin: Offset): Float {
    val dx = maxOf(origin.x, width - origin.x)
    val dy = maxOf(origin.y, height - origin.y)
    return hypot(dx, dy)
}

/** Where the ground starts growing: the backdrop slice's centre, [REVEAL_ORIGIN_OVERSHOOT] past the top-right corner. */
internal fun revealOrigin(width: Float, overshoot: Float): Offset = Offset(width + overshoot, -overshoot)

internal fun revealOriginOvershootPx(density: Density): Float =
    with(density) { REVEAL_ORIGIN_OVERSHOOT.toPx() }

/**
 * Slides and fades content in as the entrance [progress] (read only while drawing, so no recomposition) runs:
 * from below when [fromBottom], else from above. [index] staggers carousel cards.
 */
internal fun Modifier.entrance(progress: () -> Float, index: Int = 0, fromBottom: Boolean = true): Modifier =
    graphicsLayer {
        val f = entranceFraction(progress(), index)
        if (f < 1f) {
            translationY = (1f - f) * ENTRANCE_DISTANCE.toPx() * if (fromBottom) 1f else -1f
            alpha = f
        }
    }
