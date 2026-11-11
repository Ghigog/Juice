package com.juice.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.juice.R
import com.juice.settings.JuiceSettings
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import kotlin.coroutines.coroutineContext

/** The ground's alpha: lighter than the old 0.55 and 0.97, so the screen behind shows through. */
internal fun overlayGroundAlpha(blurred: Boolean): Float = if (blurred) 0.35f else 0.75f

/**
 * The overlay's ground: the mockup's dark teal-to-indigo glass. Over the blur it stays thin
 * enough for the blurred screen to show through; at 0.9 it hid the blur completely. It is drawn over the
 * slice backdrop and also backs the panels, so it stays light but dark enough for white text.
 */
internal fun overlayTint(blurred: Boolean): Brush {
    val alpha = overlayGroundAlpha(blurred)
    return Brush.linearGradient(
        listOf(Color(0xFF0F2E35), Color(0xFF0B1A22), Color(0xFF1A1838)).map { it.copy(alpha = alpha) },
    )
}

/**
 * [withFrameNanos] that tests can pause: Compose's own `withInfiniteAnimationFrameNanos` is internal, so go through the
 * public [InfiniteAnimationPolicy] it uses, which keeps this endless loop from blocking test idling.
 */
internal suspend fun <R> withInfiniteFrameNanos(onFrame: (Long) -> R): R {
    val policy = coroutineContext[InfiniteAnimationPolicy] ?: return withFrameNanos(onFrame)
    return policy.onInfiniteOperation { withFrameNanos(onFrame) }
}

const val TAG_BACKDROP_SLICE = "backdrop_slice"
const val BACKDROP_SHOWN = "shown"
const val BACKDROP_HIDDEN = "hidden"

/**
 * The big slice in the top-right corner behind the content; it takes no touches. It is drawn sharp, as a
 * plain vector draw; the ground in front of it supplies any dimming or softening.
 */
@Composable
internal fun SliceBackdrop(hud: Hud, slices: Int, active: Int, spin: SpinMomentum, shown: Boolean) {
    val painter = remember { SliceLogoPainter() }
    val style = SliceStyle.of(hud.flavour)
    var turn by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spin) {
        if (!spin.enabled) return@LaunchedEffect
        var last = withInfiniteFrameNanos { it }
        while (true) {
            val now = withInfiniteFrameNanos { it }
            turn = spin.step(now - last)
            last = now
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Matches backdropSliceSpot, where the handle's slice flies to and from.
        val side = backdropSliceDp(maxHeight.value).dp
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .offset(side / 4, -side / 4)
                .size(side)
                .semantics { stateDescription = if (shown) BACKDROP_SHOWN else BACKDROP_HIDDEN }
                .testTag(TAG_BACKDROP_SLICE)
                // Hidden while the stage's flying slice opens or closes the overlay, so one slice is on screen.
                .graphicsLayer {
                    alpha = if (shown) 1f else 0f
                    rotationZ = turn
                },
        ) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, size.width / 2, size.height / 2, size.width, style, slices, active, shadow = false) }
        }
    }
}

/** Previews the swipe distance around the handle's saved position. */
@Composable
internal fun SwipeRing(settings: JuiceSettings, hud: Hud) {
    val painter = remember { SliceLogoPainter() }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cx = constraints.maxWidth * settings.handleX.coerceIn(0f, 1f)
        val cy = constraints.maxHeight * settings.handleY.coerceIn(0f, 1f)
        val radius = with(density) { settings.swipeDistance.dp.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { painter.drawRing(it.nativeCanvas, cx, cy, radius, 0f, false, hud.accent.toArgb(), density.density) }
        }
    }
}
