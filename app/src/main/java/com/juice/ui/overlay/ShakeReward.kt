package com.juice.ui.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.liquid.Geyser
import com.juice.feedback.FeedbackEvent
import kotlin.math.min
import kotlin.random.Random

const val TAG_SHAKE_PROMPT = "shake_prompt"

private const val STEP_MS = 1000f / 60f
private val FOAM = Color(0xFFFFF8EC)

/** True while the meter reads 100%, rounded the way the hydration label rounds it; 99% is not full. */
internal fun shakeArmed(fill: Float): Boolean = Math.round(fill * 100) >= 100

/**
 * The full meter's reward, over the centre card: a big SHAKE prompt, and a [Geyser] that builds pressure
 * from [OverlayActions.shake] and, once full, sprays foam out of the top of the card. The spray may run
 * above the card, so nothing here clips. Off under reduce motion, since shaking does nothing then.
 */
@Composable
internal fun ShakeReward(hud: Hud, reduceMotion: Boolean, actions: OverlayActions, modifier: Modifier = Modifier, random: Random = Random.Default) {
    val armed = shakeArmed(hud.fill) && !reduceMotion
    val geyser = remember { Geyser(random) }
    var prompting by remember { mutableStateOf(false) }
    var pressure by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val width by rememberUpdatedState(maxWidth.value)
        val current by rememberUpdatedState(actions)
        LaunchedEffect(armed) {
            geyser.armed = armed
            prompting = geyser.prompting
            var last = -1L
            var acc = 0f
            while (geyser.busy) {
                val now = withInfiniteFrameNanos { it }
                if (last >= 0) acc += min(100f, (now - last) / 1e6f)
                last = now
                var steps = 0
                while (acc >= STEP_MS && steps < 3) {
                    if (geyser.step(current.shake(), width)) {
                        current.onErupt()
                        current.feedback(FeedbackEvent.SATIATED_PEAK)
                    }
                    acc -= STEP_MS; steps++
                }
                if (steps == 3) acc = 0f
                prompting = geyser.prompting
                pressure = geyser.pressure
                if (geyser.drops.isNotEmpty()) frame++
            }
            prompting = geyser.prompting
            frame++
        }
        val accent = hud.accent
        Canvas(Modifier.fillMaxSize()) {
            frame // redraw as the drops move
            for (d in geyser.drops) {
                val fade = 1f - d.age.toFloat() / d.life
                val at = Offset(d.x * density, d.y * density)
                drawCircle(accent.copy(alpha = 0.5f * fade), d.r * 1.4f * density, at)
                drawCircle(FOAM.copy(alpha = 0.9f * fade), d.r * density, at)
            }
        }
        if (prompting) ShakePrompt(accent, { pressure }, Modifier.align(Alignment.Center))
    }
}

/** Big wobbling letters; they shake harder and swell as [pressure] (0..1) builds. */
@Composable
private fun ShakePrompt(accent: Color, pressure: () -> Float, modifier: Modifier) {
    val wobble by rememberInfiniteTransition(label = "shake").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "wobble",
    )
    Box(modifier) {
        Text(
            stringResource(R.string.overlay_shake),
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            color = Color.White,
            style = LocalTextStyle.current.merge(TextStyle(shadow = Shadow(accent.copy(alpha = 0.9f), Offset(0f, 4f), 18f))),
            modifier = Modifier
                .graphicsLayer {
                    val p = pressure()
                    rotationZ = wobble * (4f + p * 10f)
                    translationX = wobble * p * 6.sp.toPx()
                    scaleX = 1f + p * 0.35f
                    scaleY = 1f + p * 0.35f
                }
                .testTag(TAG_SHAKE_PROMPT),
        )
    }
}
