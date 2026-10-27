package com.juice.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.juice.core.character.Character
import com.juice.core.character.Characters
import com.juice.core.character.Expression
import com.juice.core.character.Face
import com.juice.core.character.Monogram
import com.juice.core.character.face.FaceAnimator
import com.juice.core.character.face.FaceFrame
import com.juice.core.character.face.FaceMotion
import com.juice.core.character.face.LidCutter
import kotlin.coroutines.coroutineContext

/** The face's palette: a cream body over a pop shadow in the character's own colour. */
object FaceColors {
    val Cream = Color(0xFFFFF8EC)

    /** A built-in's colour; a created character's comes from its monogram hue so it stays stable. */
    fun of(character: Character): Color = when (character.id) {
        Characters.Jim.id -> Color(0xFFFF8A3D)
        Characters.Nutritionist.id -> Color(0xFFF0489E)
        Characters.SleepScientist.id -> Color(0xFFA78BFA)
        else -> Color.hsv(Monogram(character).hue.toFloat(), 0.6f, 0.95f)
    }
}

/** The face is laid out on 100 x 100 units; this much room is kept around it for wobble, hops and froth. */
private const val VIEW_MARGIN = 10f
private const val VIEW_SIZE = 100f + 2 * VIEW_MARGIN

/** How far the shadow layer sits from the body, in face units. */
private const val SHADOW_X = 2.5f
private const val SHADOW_Y = 3f

/** Rounds the corners of every feature, in face units. */
private const val SOFT_EDGE = 3f

/**
 * A living face for [character] that springs to [expression] whenever it changes. Hoist it to drive the
 * mouth: call [FaceAnimator.letter] as a line types out. With [animate] false (reduce motion) it holds still.
 */
@Composable
fun rememberFaceAnimator(character: Character, expression: Expression, animate: Boolean = true): FaceAnimator {
    val animator = remember(character.id) { FaceAnimator(character.face, FaceMotion.of(character), expression, calm = !animate) }
    SideEffect { animator.calm = !animate }
    LaunchedEffect(animator, expression) { animator.set(expression) }
    return animator
}

/**
 * Draws [animator] every frame: its eyes and mouth, a pop shadow in [shadow] and the froth it bursts.
 * Only drawing reruns per frame; nothing recomposes.
 */
@Composable
fun AnimatedCharacterFace(
    animator: FaceAnimator,
    shadow: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    var drawnAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animator) {
        var last = withInfiniteFrameNanos { it }
        while (true) {
            val now = withInfiniteFrameNanos { it }
            animator.step((now - last) / 1_000_000_000f)
            last = now
            drawnAt = now
        }
    }
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        drawnAt // Read while drawing, so each frame redraws without recomposing.
        drawFace(animator.frame(), FaceColors.Cream, shadow, glow = true)
    }
}

/** A still face holding [expression], e.g. for a list avatar; it fills the smaller side of its bounds. */
@Composable
fun CharacterFace(face: Face, expression: Expression, modifier: Modifier = Modifier, color: Color = Color.White, shadow: Color? = null) {
    val frame = remember(face, expression) { FaceAnimator.still(face, expression) }
    Canvas(modifier.size(96.dp)) { drawFace(frame, color, shadow, glow = false) }
}

private fun DrawScope.drawFace(frame: FaceFrame, body: Color, shadow: Color?, glow: Boolean) {
    val unit = minOf(size.width, size.height) / VIEW_SIZE
    val left = (size.width - VIEW_SIZE * unit) / 2f + VIEW_MARGIN * unit
    val top = (size.height - VIEW_SIZE * unit) / 2f + VIEW_MARGIN * unit
    translate(left, top) {
        scale(unit, unit, pivot = Offset.Zero) {
            val pivot = Offset(LidCutter.PIVOT_X, LidCutter.PIVOT_Y)
            if (glow && shadow != null) drawCircle(shadow.copy(alpha = 0.14f), radius = 46f, center = pivot)
            translate(frame.offsetX, frame.offsetY) {
                rotate(frame.rotation, pivot) {
                    scale(frame.scaleX, frame.scaleY, pivot) {
                        if (shadow != null) {
                            frame.bubbles.forEach { drawCircle(shadow.copy(alpha = 0.85f), it.radius, Offset(it.x, it.y)) }
                        }
                        val paths = frame.polygons.mapNotNull { it.toPath() }
                        if (shadow != null) translate(SHADOW_X, SHADOW_Y) { paths.forEach { drawSoft(it, shadow) } }
                        paths.forEach { drawSoft(it, body) }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSoft(path: Path, color: Color) {
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = SOFT_EDGE, join = StrokeJoin.Round))
}

private fun FloatArray.toPath(): Path? {
    if (size < 6) return null
    return Path().apply {
        moveTo(this@toPath[0], this@toPath[1])
        var i = 2
        while (i < this@toPath.size - 1) {
            lineTo(this@toPath[i], this@toPath[i + 1])
            i += 2
        }
        close()
    }
}

/**
 * [withFrameNanos] that tests can pause: Compose's own `withInfiniteAnimationFrameNanos` is internal, so go through the
 * public [InfiniteAnimationPolicy] it uses, which keeps this endless loop from blocking test idling.
 */
private suspend fun <R> withInfiniteFrameNanos(onFrame: (Long) -> R): R {
    val policy = coroutineContext[InfiniteAnimationPolicy] ?: return withFrameNanos(onFrame)
    return policy.onInfiniteOperation { withFrameNanos(onFrame) }
}
