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

@Composable
internal fun Header(
    hud: Hud,
    entrance: () -> Float,
    pop: Modifier,
    onRecap: () -> Unit,
    onRoutines: () -> Unit,
    onSettings: () -> Unit,
    onRoutinesBounds: (Rect) -> Unit,
    /** Shown between the level and the multiplier, where a wide screen has room. */
    middle: (@Composable () -> Unit)? = null,
    /** The categories' names, Main first, and which one is current. */
    categoryNames: List<String> = listOf("Main"),
    categoryIndex: Int = 0,
    onCategory: () -> Unit = {},
    reduceMotion: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().entrance(entrance, fromBottom = false).then(pop)) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text(stringResource(R.string.overlay_level, hud.level), fontSize = JuiceType.Label, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text(
                    hud.flavour.displayName,
                    fontSize = JuiceType.Label,
                    color = JuiceColors.white(0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(JuiceShapes.Capsule).background(JuiceColors.white(0.18f))) {
                Box(Modifier.fillMaxWidth(hud.levelProgress).fillMaxHeight().clip(JuiceShapes.Capsule).background(hud.accent))
            }
        }
        if (middle != null) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp)) { middle() }
        }
        }
        MultiplierBadge(hud)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(String.format("%,d", hud.juice), fontSize = JuiceType.SheetHeading, fontWeight = FontWeight.Bold, lineHeight = JuiceType.BodyLine)
            Text(stringResource(R.string.overlay_juice_label), fontSize = JuiceType.Badge, letterSpacing = 1.sp, color = JuiceColors.white(0.75f))
        }
        Spacer(Modifier.width(8.dp))
        val recapLabel = stringResource(R.string.recap_open)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(JuiceColors.white(0.14f))
                .clickable(onClick = onRecap)
                .semantics { contentDescription = recapLabel }
                .testTag(TAG_RECAP),
            contentAlignment = Alignment.Center,
        ) {
            // Three bars stepping down, like a burndown.
            Canvas(Modifier.size(20.dp)) {
                val u = size.width / 24f
                fun bar(x: Float, top: Float) =
                    drawRoundRect(Color.White, Offset(x * u, top * u), androidx.compose.ui.geometry.Size(4f * u, (20f - top) * u), androidx.compose.ui.geometry.CornerRadius(u))
                bar(3f, 4f); bar(10f, 10f); bar(17f, 15f)
            }
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
                drawArc(Color.White, 40f, 280f, false, Offset(4f * u, 4f * u), androidx.compose.ui.geometry.Size(16f * u, 16f * u), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(
                    Path().apply { moveTo(16f * u, 1f * u); lineTo(21f * u, 6f * u); lineTo(14f * u, 7f * u); close() },
                    Color.White,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val label = stringResource(R.string.settings_open)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(JuiceColors.white(0.14f))
                .clickable(onClick = onSettings)
                .semantics { contentDescription = label }
                .testTag(TAG_SETTINGS),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val w = 2.dp.toPx()
                val u = size.width / 24f
                fun line(x0: Float, y: Float, x1: Float) =
                    drawLine(Color.White, Offset(x0 * u, y * u), Offset(x1 * u, y * u), w, StrokeCap.Round)
                line(4f, 7f, 14f); line(18f, 7f, 20f); line(4f, 17f, 6f); line(10f, 17f, 20f)
                drawCircle(Color.White, 2f * u, Offset(16f * u, 7f * u), style = Stroke(w))
                drawCircle(Color.White, 2f * u, Offset(8f * u, 17f * u), style = Stroke(w))
            }
        }
    }
    CategoryStrip(categoryNames.getOrElse(categoryIndex) { "" }, categoryNames.size, categoryIndex, hud, reduceMotion, onCategory)
    }
}

const val TAG_CATEGORY_CAPSULE = "category_capsule"
const val TAG_CATEGORY_DOT_PREFIX = "category_dot_"

/** The current category's name in a glass capsule, with one dot per category beside it; tapping the capsule renames. */
@Composable
private fun CategoryStrip(name: String, count: Int, current: Int, hud: Hud, reduceMotion: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        if (pressed && !reduceMotion) 0.94f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "categorySqueeze",
    )
    val dip = if (pressed && reduceMotion) 0.7f else 1f
    Row(Modifier.fillMaxWidth().height(MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
        // The tappable box is the full touch target; the glass capsule inside keeps its own height.
        Box(
            Modifier.height(MinTouchTarget).widthIn(max = 200.dp).touchTarget(onClick, interactionSource = source).testTag(TAG_CATEGORY_CAPSULE),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name,
                Modifier
                    .graphicsLayer { scaleX = squeeze; scaleY = squeeze; alpha = dip }
                    .height(30.dp)
                    .clip(JuiceShapes.Capsule)
                    .background(JuiceColors.white(0.14f))
                    .border(1.dp, JuiceColors.white(0.25f), JuiceShapes.Capsule)
                    .padding(horizontal = 16.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                fontSize = JuiceType.Label,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (i == current) hud.accent else JuiceColors.white(0.3f))
                        .semantics { selected = i == current }
                        .testTag(TAG_CATEGORY_DOT_PREFIX + i),
                )
            }
        }
    }
}
