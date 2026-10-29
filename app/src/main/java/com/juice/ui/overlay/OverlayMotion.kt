package com.juice.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** How long the ground takes to grow from the backdrop slice to cover the screen; must match SliceHandle's FLIGHT_MILLIS. */
internal const val REVEAL_MILLIS = 320

/** How long the ground takes to drain back into the backdrop slice when the overlay closes; must match SliceHandle's return flight. */
internal const val CLOSE_DRAIN_MILLIS = 200

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
 * The sideways sway, -1..1, of an item [fraction] (0..1) of the way through its entrance: it wobbles
 * back and forth while the swing dies away, so it is at rest at both ends.
 */
internal fun entranceSway(fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return kotlin.math.sin(f * SWAY_CYCLES * 2f * Math.PI.toFloat()) * (1f - f)
}

private const val SWAY_CYCLES = 1.5f
private val ENTRANCE_SWAY = 14.dp

/**
 * Bubbles content in as the entrance [progress] (read only while drawing, so no recomposition) runs:
 * rising from below when [fromBottom], else sinking from above, while swaying side to side as it settles.
 * [index] staggers carousel cards.
 */
internal fun Modifier.entrance(progress: () -> Float, index: Int = 0, fromBottom: Boolean = true): Modifier =
    graphicsLayer {
        val f = entranceFraction(progress(), index)
        if (f < 1f) {
            translationY = (1f - f) * ENTRANCE_DISTANCE.toPx() * if (fromBottom) 1f else -1f
            translationX = entranceSway(f) * ENTRANCE_SWAY.toPx()
            alpha = f
        }
    }

/** How long the content takes to swell and burst when the overlay closes; the ground drains afterwards over [CLOSE_DRAIN_MILLIS]. */
internal const val POP_MILLIS = 220

/** Gap between visible cards starting to pop when the overlay closes, and how long each card's pop takes. */
internal const val CARD_POP_GAP_MILLIS = 50
internal const val CARD_POP_MILLIS = 140

/** When the pop of the [index]th visible card (top to bottom) starts, in milliseconds after the closing begins. */
internal fun cardPopStartMillis(index: Int): Int = index * CARD_POP_GAP_MILLIS

/** How long popping [count] visible cards takes, from the first starting to the last one gone; 0 when there are none. */
internal fun cardPopTotalMillis(count: Int): Int = if (count <= 0) 0 else cardPopStartMillis(count - 1) + CARD_POP_MILLIS

/** How far the [index]th visible card's pop is, 0..1, [elapsedMillis] after the closing began. */
internal fun cardPopProgress(elapsedMillis: Float, index: Int): Float =
    ((elapsedMillis - cardPopStartMillis(index)) / CARD_POP_MILLIS).coerceIn(0f, 1f)

/**
 * When the pop of a non-card element starts, in milliseconds after the closing began: after the last of [cardCount] visible
 * cards (at 0 with none). Slot 0 is the centre card (and header), slot 1 the capture field (and coach chat), one gap apart.
 */
internal fun elementPopStartMillis(cardCount: Int, slot: Int): Int = cardPopStartMillis(cardCount.coerceAtLeast(0) + slot)

/** How far a non-card element's pop is, 0..1, [elapsedMillis] after the closing began. */
internal fun elementPopProgress(elapsedMillis: Float, cardCount: Int, slot: Int): Float =
    ((elapsedMillis - elementPopStartMillis(cardCount, slot)) / CARD_POP_MILLIS).coerceIn(0f, 1f)

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

/** Each card's explosion is seeded by its rank on top of this, so cards differ but a given rank always bursts the same. */
private const val CARD_EXPLOSION_SEED = 470L

/** The juice bursting from the card of [rank] (top to bottom) whose centre is [centre]. */
internal fun cardExplosionBubbles(centre: Offset, rank: Int, maxDistance: Float, maxRadius: Float): List<FrothBubble> =
    frothBubbles(listOf(centre), CARD_EXPLOSION_SEED + rank, maxDistance, maxRadius)

/** Whether a card's explosion is drawn at pop [progress]: only between its pop starting and finishing. */
internal fun cardExplosionVisible(progress: Float): Boolean = progress > 0f && progress < 1f

/** Where a [bubble] is, and how wide, when the pop is at [progress]: it flies out and shrinks away. */
internal fun FrothBubble.at(progress: Float): Pair<Offset, Float> {
    val p = progress.coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(p)
    val centre = Offset(origin.x + kotlin.math.cos(angle) * distance * eased, origin.y + kotlin.math.sin(angle) * distance * eased)
    return centre to radius * (1f - p)
}

/** The shape a card is clipped to, exposed so a test can see the clip without capturing pixels. */
internal val ClippedShape = SemanticsPropertyKey<Shape>("ClippedShape")

/** Clips to [shape] and reports it, so a card's corners are rounded on every frame, whatever is drawn into it. */
internal fun Modifier.roundedClip(shape: Shape): Modifier =
    clip(shape).semantics { this[ClippedShape] = shape }
