package com.juice.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** How long the ground takes to grow from the backdrop slice to cover the screen; must match SliceHandle's FLIGHT_MILLIS. */
internal const val REVEAL_MILLIS = 320

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

/** How long the content takes to swell and burst when the overlay closes; the ground drains afterwards over [REVEAL_MILLIS]. */
internal const val POP_MILLIS = 320

/** The share of the pop spent swelling before the burst, and the largest the content swells to. */
private const val SWELL_SHARE = 0.3f
private const val SWELL_SCALE = 1.08f

/** The content's scale as the pop [progress] (0..1) runs: a quick swell to [SWELL_SCALE], then down to nothing. */
internal fun popScale(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return if (p < SWELL_SHARE) 1f + (SWELL_SCALE - 1f) * (p / SWELL_SHARE)
    else SWELL_SCALE * (1f - (p - SWELL_SHARE) / (1f - SWELL_SHARE))
}

/** The content's alpha as the pop [progress] runs: untouched while swelling, then fading out with the burst. */
internal fun popAlpha(progress: Float): Float =
    1f - ((progress - SWELL_SHARE) / (1f - SWELL_SHARE)).coerceIn(0f, 1f)

/** One froth bubble: it leaves [origin], flying [distance] along [angle] (radians), and is [radius] wide. */
internal data class FrothBubble(val origin: Offset, val angle: Float, val distance: Float, val radius: Float)

private const val BUBBLES_PER_ORIGIN = 14

/** Froth bursting from each of [origins]; the same [seed] always gives the same bubbles. */
internal fun frothBubbles(origins: List<Offset>, seed: Long, maxDistance: Float, maxRadius: Float): List<FrothBubble> {
    val random = kotlin.random.Random(seed)
    return origins.flatMap { origin ->
        List(BUBBLES_PER_ORIGIN) {
            FrothBubble(
                origin = origin,
                angle = random.nextFloat() * 2f * Math.PI.toFloat(),
                distance = maxDistance * (0.3f + 0.7f * random.nextFloat()),
                radius = maxRadius * (0.3f + 0.7f * random.nextFloat()),
            )
        }
    }
}

/** Where a [bubble] is, and how wide, when the pop is at [progress]: it flies out and shrinks away. */
internal fun FrothBubble.at(progress: Float): Pair<Offset, Float> {
    val p = progress.coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(p)
    val centre = Offset(origin.x + kotlin.math.cos(angle) * distance * eased, origin.y + kotlin.math.sin(angle) * distance * eased)
    return centre to radius * (1f - p)
}
