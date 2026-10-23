package com.juice.ui.character

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import com.juice.core.character.Character
import com.juice.core.character.Monogram
import kotlin.math.roundToInt

/** A character's [Monogram] in a circle of [size]; with [animate] it bobs and pulses gently, otherwise it stays still (pass `!reduceMotion`). */
@Composable
fun CharacterAvatar(character: Character, size: Dp, animate: Boolean, modifier: Modifier = Modifier) {
    val monogram = remember(character) { Monogram(character) }
    val bobPx = with(LocalDensity.current) { size.toPx() * BOB_FRACTION }
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "avatar-idle")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(IDLE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
            label = "avatar-phase",
        ).value
    } else {
        0f
    }
    val scale = 1f + PULSE * phase
    Box(
        Modifier
            .offset { IntOffset(0, (-bobPx * phase).roundToInt()) }
            .then(modifier)
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.hsv(monogram.hue.toFloat(), 0.55f, 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(monogram.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = TextUnit(size.value * 0.4f, androidx.compose.ui.unit.TextUnitType.Sp))
    }
}

private const val IDLE_MILLIS = 1600
private const val BOB_FRACTION = 0.06f
private const val PULSE = 0.03f
