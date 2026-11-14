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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.ui.Tier
import kotlinx.coroutines.delay

/** The visible undo pill inside its 44 dp touch target. */
internal const val TAG_UNDO_PILL = "undo_pill"

@Composable
internal fun UndoToast(undo: Undo, hud: Hud, onUndo: () -> Unit) {
    val left = remember(undo) { Animatable(1f) }
    LaunchedEffect(undo) { left.animateTo(0f, tween(OverlayShellState.UNDO_WINDOW_MILLIS.toInt(), easing = LinearEasing)) }
    Box(
        Modifier
            .fillMaxSize()
            .clip(JuiceShapes.Bubble)
            .background(JuiceColors.Toast)
            .border(1.dp, JuiceColors.white(0.18f), JuiceShapes.Bubble),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (undo.dismissed) R.string.overlay_undo_archived else R.string.overlay_undo_completed, undo.item.title),
                fontSize = JuiceType.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.testTag(TAG_UNDO).touchTarget(onUndo), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .height(32.dp)
                        .testTag(TAG_UNDO_PILL)
                        .clip(JuiceShapes.Capsule)
                        .background(hud.accent)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.overlay_undo), color = JuiceColors.Ink, fontSize = JuiceType.Label, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(left.value).height(3.dp).background(hud.accent))
    }
}

/** The tier and fill percent, e.g. "HYDRATED: 37%", above the centre card. */
@Composable
internal fun HydrationLabel(hud: Hud, modifier: Modifier = Modifier) {
    val label = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f))
    Text(
        stringResource(R.string.overlay_thirst, hud.tier.name, Math.round(hud.fill * 100)),
        fontSize = JuiceType.Badge,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
        style = LocalTextStyle.current.merge(label),
        modifier = modifier.testTag(TAG_HYDRATION),
    )
}

/** The multiplier pill; it pulses while Satiated. */
@Composable
internal fun MultiplierBadge(hud: Hud) {
    val satiated = hud.tier == Tier.SATIATED
    val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
        1f, if (satiated) 1.08f else 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .widthIn(min = 56.dp)
            .height(34.dp)
            .clip(JuiceShapes.Capsule)
            .background(if (satiated) hud.accent.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f))
            .border(if (satiated) 2.dp else 1.dp, hud.accent, JuiceShapes.Capsule)
            .padding(horizontal = 12.dp)
            .testTag(TAG_MULTIPLIER),
        contentAlignment = Alignment.Center,
    ) {
        Text(hud.multiplierLabel, fontSize = JuiceType.Dialogue, fontWeight = FontWeight.Bold)
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
internal fun Banner(effects: OverlayEffects, hud: Hud, modifier: Modifier) {
    val banner = effects.banner
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(maxOf(BANNER_MILLIS, banner.text.length * BANNER_MILLIS_PER_CHAR))
            effects.clearBanner(banner.id)
        }
    }
    AnimatedVisibility(banner != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        var last by remember { mutableStateOf(ShownBanner(0, "", TAG_BANNER)) }
        if (banner != null) last = banner
        Box(
            Modifier
                .width(310.dp)
                .heightIn(min = 40.dp)
                .clip(JuiceShapes.Pill)
                .background(hud.accent)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(last.tag),
            contentAlignment = Alignment.Center,
        ) {
            Text(last.text, color = JuiceColors.Ink, fontSize = JuiceType.Body, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** "+250 x2.4" rising and fading over the card. */
@Composable
internal fun Floats(effects: OverlayEffects, hud: Hud, modifier: Modifier) {
    Box(modifier) {
        effects.floats.forEach { float ->
            val progress = remember(float.id) { Animatable(0f) }
            LaunchedEffect(float.id) {
                progress.animateTo(1f, tween(FLOAT_MILLIS, easing = LinearEasing))
                effects.clearFloat(float.id)
            }
            Text(
                float.text,
                fontSize = JuiceType.Stat,
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
