package com.juice.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import com.juice.feedback.FeedbackEvent
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun Header(
    hud: Hud,
    entrance: () -> Float,
    pop: Modifier,
    onRecap: () -> Unit,
    onRoutines: () -> Unit,
    onSettings: () -> Unit,
    onRoutinesBounds: (Rect) -> Unit,
    /** Shown between the Level block and the category name, where there is room. */
    middle: (@Composable () -> Unit)? = null,
    /** The categories' names, Main first, and which one is current. */
    categoryOrder: List<ShelfCategory> = listOf(ShelfCategory("Main", movable = false)),
    categoryIndex: Int = 0,
    onCategory: () -> Unit = {},
    onMoveCategory: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onMoveEarlier: (index: Int) -> Unit = {},
    onMoveLater: (index: Int) -> Unit = {},
    onShelfTick: () -> Unit = {},
    /** The SNAP cue for the Settings tap. */
    onFeedback: (FeedbackEvent) -> Unit = {},
    /** Glows the current category's dot after a drop on the shelf; the dots live outside the header. */
    dropGlow: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    reduceMotion: Boolean = false,
) {
    var shelfOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).entrance(entrance, fromBottom = false).then(pop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The unfurled shelf takes the whole bar; everything else steps aside while it is open.
        if (!shelfOpen) {
            LevelBlock(
                hud, reduceMotion, Modifier.weight(1f),
                onSettings = { onFeedback(FeedbackEvent.SNAP); onSettings() },
                onRecap = onRecap,
            )
            if (middle != null) {
                Box(Modifier.weight(1f).height(44.dp).padding(horizontal = 8.dp)) { middle() }
            }
            MultiplierBadge(hud)
            Spacer(Modifier.width(10.dp))
        }
        Box(
            if (shelfOpen) Modifier.weight(1f) else Modifier.widthIn(max = 150.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            CategoryStrip(
                categoryOrder, categoryIndex, reduceMotion, onCategory, onMoveCategory, onMoveEarlier, onMoveLater, onShelfTick,
                glow = dropGlow, onOpenChange = { shelfOpen = it },
            )
        }
        Spacer(Modifier.width(8.dp))
        val routinesLabel = stringResource(R.string.routines_open)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(JuiceColors.white(0.14f))
                .onGloballyPositioned { onRoutinesBounds(it.boundsInRoot()) }
                .clickable(onClick = onRoutines)
                .semantics { contentDescription = routinesLabel }
                .testTag(TAG_ROUTINES),
            contentAlignment = Alignment.Center,
        ) {
            // A circling arrow: the Routine comes round again.
            Canvas(Modifier.size(20.dp)) {
                val u = size.width / 24f
                drawArc(Color.White, 40f, 280f, false, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(
                    Path().apply { moveTo(16f * u, 1f * u); lineTo(21f * u, 6f * u); lineTo(14f * u, 7f * u); close() },
                    Color.White,
                )
            }
        }
    }
}

/** "Level 9 ⚙ - 6,788" over the XP bar. The level (and cog) opens Settings; the points open Recap. */
@Composable
private fun LevelBlock(hud: Hud, reduceMotion: Boolean, modifier: Modifier, onSettings: () -> Unit, onRecap: () -> Unit) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SqueezeTarget(TAG_SETTINGS, stringResource(R.string.settings_open), reduceMotion, onSettings) {
                Text(stringResource(R.string.overlay_level, hud.level), fontSize = JuiceType.Label, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                val size = with(LocalDensity.current) { JuiceType.Label.toDp() }
                Canvas(Modifier.size(size)) {
                    val u = this.size.width / 24f
                    val w = 1.5.dp.toPx()
                    drawCircle(Color.White, 4f * u, Offset(12f * u, 12f * u), style = Stroke(w))
                    // Eight teeth around the hub.
                    for (k in 0 until 8) {
                        val a = Math.toRadians(k * 45.0)
                        val dx = cos(a).toFloat()
                        val dy = sin(a).toFloat()
                        drawLine(
                            Color.White,
                            Offset((12f + 7f * dx) * u, (12f + 7f * dy) * u),
                            Offset((12f + 10f * dx) * u, (12f + 10f * dy) * u),
                            w * 1.4f, StrokeCap.Round,
                        )
                    }
                }
            }
            Text(" - ", fontSize = JuiceType.Label, fontWeight = FontWeight.Bold, color = JuiceColors.white(0.75f), maxLines = 1)
            SqueezeTarget(TAG_RECAP, stringResource(R.string.recap_open), reduceMotion, onRecap) {
                Text(String.format("%,d", hud.juice), fontSize = JuiceType.Label, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        // The labels' touch targets are taller than their text; the bar tucks up under the text.
        Box(
            Modifier.offset(y = (-9).dp).fillMaxWidth().height(6.dp).clip(JuiceShapes.Capsule).background(JuiceColors.white(0.18f)),
        ) {
            Box(Modifier.fillMaxWidth(hud.levelProgress).fillMaxHeight().clip(JuiceShapes.Capsule).background(hud.accent))
        }
    }
}

/** A tappable label that squeezes like the category capsule (a plain alpha dip with reduced motion). */
@Composable
private fun SqueezeTarget(tag: String, description: String, reduceMotion: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        if (pressed && !reduceMotion) 0.94f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "headerSqueeze",
    )
    val dip by animateFloatAsState(if (pressed && reduceMotion) 0.7f else 1f, snap(), label = "headerDip")
    Row(
        Modifier
            .graphicsLayer { scaleX = squeeze; scaleY = squeeze; alpha = dip }
            .touchTarget(onClick, interactionSource = source)
            .padding(horizontal = 2.dp)
            .semantics { contentDescription = description }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** One dot per category, the current one in the accent. */
@Composable
internal fun CategoryDots(count: Int, current: Int, hud: Hud, glow: Animatable<Float, AnimationVector1D>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .then(
                        if (i == current) Modifier.drawBehind {
                            val g = glow.value
                            if (g > 0f) drawCircle(hud.accent.copy(alpha = 0.5f * g), radius = size.width * (0.5f + 1.2f * g))
                        } else Modifier,
                    )
                    .clip(CircleShape)
                    .background(if (i == current) hud.accent else JuiceColors.white(0.3f))
                    .semantics { selected = i == current }
                    .testTag(TAG_CATEGORY_DOT_PREFIX + i),
            )
        }
    }
}
