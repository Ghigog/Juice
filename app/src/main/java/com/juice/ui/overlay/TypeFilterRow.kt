package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.core.intake.ItemType
import kotlinx.coroutines.launch

const val TAG_TYPE_FILTER_ROW = "type_filter_row"
const val TAG_FLOATING_TYPE_PREFIX = "floating_type_"

private const val ENTER_OFFSET_DP = 28f
private const val BOB_DP = 3f
private const val BOB_MILLIS = 1400
private const val JINGLE_DEGREES = 9f
private const val WIGGLE_STEP_MILLIS = 70
private const val FADE_MILLIS = 160
private const val FLY_BACK_MILLIS = 240

/**
 * The floating types above the card row, in their colours. A type enters with a jingle and a spring up, bobs, and
 * on a tap flies back down and fades while [onRelease] drops it from the filter. The row takes no height when
 * nothing floats. With [reduceMotion] the bounce, wiggle and bob become a fade.
 */
@Composable
internal fun TypeFilterRow(
    types: Set<ItemType>,
    reduceMotion: Boolean,
    onRelease: (ItemType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A released type stays here until its exit animation has played.
    var present by remember { mutableStateOf(types.toList()) }
    val merged = present + types.filter { it !in present }
    if (merged != present) present = merged
    Row(
        modifier.fillMaxWidth().animateContentSize().testTag(TAG_TYPE_FILTER_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        present.forEach { type ->
            key(type) {
                FloatingType(
                    type, leaving = type !in types, reduceMotion = reduceMotion,
                    onRelease = { onRelease(type) },
                    onGone = { present = present - type },
                )
            }
        }
    }
}

@Composable
private fun FloatingType(
    type: ItemType,
    leaving: Boolean,
    reduceMotion: Boolean,
    onRelease: () -> Unit,
    onGone: () -> Unit,
) {
    // 0 is down and invisible, 1 is hovering; the spring overshoots past 1.
    val lift = remember { Animatable(0f) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            lift.animateTo(0f, tween(if (reduceMotion) FADE_MILLIS else FLY_BACK_MILLIS))
            onGone()
        } else if (reduceMotion) {
            lift.animateTo(1f, tween(FADE_MILLIS))
        } else {
            launch {
                listOf(-1f, 1f, -0.6f, 0.6f, 0f).forEach { wiggle.animateTo(it * JINGLE_DEGREES, tween(WIGGLE_STEP_MILLIS)) }
            }
            lift.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
    }
    val bob = if (reduceMotion || leaving) 0f else {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(
            -BOB_DP, BOB_DP, infiniteRepeatable(tween(BOB_MILLIS, easing = LinearEasing), RepeatMode.Reverse), label = "bob",
        ).value
    }
    Box(
        Modifier
            .testTag(TAG_FLOATING_TYPE_PREFIX + type.name)
            .graphicsLayer {
                alpha = lift.value.coerceIn(0f, 1f)
                if (!reduceMotion) {
                    translationY = (1f - lift.value) * ENTER_OFFSET_DP * density + bob * density
                    rotationZ = wiggle.value
                }
            }
            .touchTarget(onClick = { if (!leaving) onRelease() }, enabled = !leaving),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(28.dp)
                .clip(JuiceShapes.Capsule)
                .background(JuiceColors.of(type))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(type.name.uppercase(), color = JuiceColors.Ink, fontSize = JuiceType.Badge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
