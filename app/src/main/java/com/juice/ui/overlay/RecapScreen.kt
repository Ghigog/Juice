package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.model.Item
import com.juice.core.recap.Burndown
import com.juice.core.recap.Recap
import com.juice.core.recap.RecapBuilder
import com.juice.core.recap.RecapPeriod
import com.juice.feedback.FeedbackEvent
import java.time.Clock
import kotlin.math.roundToInt

const val TAG_RECAP_SCREEN = "recap_screen"
const val TAG_RECAP_CLOSE = "recap_close"
const val TAG_RECAP_SECTION_PREFIX = "recap_section_"
const val TAG_RECAP_COUNT_PREFIX = "recap_count_"
const val TAG_RECAP_EMPTY_PREFIX = "recap_empty_"
const val TAG_RECAP_CHART_PREFIX = "recap_chart_"

private const val ENTRANCE_MILLIS = 450
private const val COUNT_MILLIS = 900
private const val CHART_MILLIS = 1100

/**
 * The Recap: what was completed today, this week and this month, each with its burndown (design doc §5.5).
 * Loads its items through [actions] and builds the three [Recap]s from them.
 */
@Composable
fun RecapScreen(
    hud: Hud,
    reduceMotion: Boolean,
    clock: Clock,
    actions: OverlayActions,
    onClose: () -> Unit,
    systemBars: PaddingValues = PaddingValues(0.dp),
    /** The gap under the status bar: smaller on a phone on its side. */
    topGap: Dp = 38.dp,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    var items by remember { mutableStateOf<List<Item>?>(null) }
    LaunchedEffect(Unit) {
        actions.feedback(FeedbackEvent.RECAP_OPEN)
        // The Week may begin before the Month, so look back as far as either does.
        val since = minOf(RecapPeriod.Week.range(clock).start, RecapPeriod.Month.range(clock).start)
        items = actions.recapItems(since)
    }
    val recaps = remember(items) {
        items?.let { list -> RecapPeriod.values().map { RecapBuilder.build(list, it, clock) } }.orEmpty()
    }
    val closeLabel = stringResource(R.string.recap_close)
    Column(
        modifier
            .fillMaxSize()
            .background(JuiceColors.Ink)
            .padding(
                top = systemBars.calculateTopPadding() + topGap,
                start = systemBars.calculateStartPadding(layoutDirection) + 14.dp,
                end = systemBars.calculateEndPadding(layoutDirection) + 14.dp,
                bottom = systemBars.calculateBottomPadding(),
            )
            .testTag(TAG_RECAP_SCREEN),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recap_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(JuiceColors.white(0.14f))
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = closeLabel }
                    .testTag(TAG_RECAP_CLOSE),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 18.sp) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(recaps, key = { it.period }) { recap ->
                RecapSection(recap, hud, reduceMotion, onReveal = { actions.feedback(FeedbackEvent.RECAP_REVEAL) })
            }
        }
    }
}

private fun RecapPeriod.title(): Int = when (this) {
    RecapPeriod.Today -> R.string.recap_today
    RecapPeriod.Week -> R.string.recap_week
    RecapPeriod.Month -> R.string.recap_month
}

@Composable
private fun RecapSection(recap: Recap, hud: Hud, reduceMotion: Boolean, onReveal: () -> Unit) {
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val counter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val chart = remember { Animatable(if (reduceMotion) 1f else 0f) }
    // A lazy item is composed as it scrolls into view, which is the moment it is revealed.
    LaunchedEffect(Unit) {
        onReveal()
        if (!reduceMotion) {
            entrance.animateTo(1f, tween(ENTRANCE_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            counter.animateTo(1f, tween(COUNT_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            chart.animateTo(1f, tween(CHART_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val name = recap.period.name
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 24.dp.toPx()
            }
            .clip(RoundedCornerShape(20.dp))
            .background(JuiceColors.white(0.08f))
            .padding(16.dp)
            .testTag(TAG_RECAP_SECTION_PREFIX + name),
    ) {
        Text(stringResource(recap.period.title()), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = hud.accent)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                (recap.completedCount * counter.value).roundToInt().toString(),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(TAG_RECAP_COUNT_PREFIX + name),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recap_completed_label), fontSize = 13.sp, color = JuiceColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.recap_juice_size, String.format("%,d", (recap.size * counter.value).toLong())),
                fontSize = 14.sp,
                color = JuiceColors.Muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (recap.groups.isEmpty()) {
            Text(stringResource(R.string.recap_empty), fontSize = 13.sp, color = JuiceColors.Muted, modifier = Modifier.padding(top = 8.dp).testTag(TAG_RECAP_EMPTY_PREFIX + name))
        }
        recap.groups.forEach { group ->
            val color = JuiceColors.of(group.type)
            Text(group.type.name.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 12.dp))
            group.entries.forEach { entry ->
                Text(entry.item.title, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                entry.steps.forEach { step ->
                    Text(stringResource(R.string.recap_step_done, step.title), fontSize = 12.sp, color = JuiceColors.Muted, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                }
            }
        }
        Text(stringResource(R.string.recap_burndown), fontSize = 11.sp, letterSpacing = 1.sp, color = JuiceColors.Muted, modifier = Modifier.padding(top = 16.dp))
        Text(
            stringResource(R.string.recap_open_at_start, recap.burndown.actual.first().open),
            fontSize = 12.sp,
            color = JuiceColors.Muted,
        )
        BurndownChart(
            recap, hud, chart.value,
            Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp).testTag(TAG_RECAP_CHART_PREFIX + name),
        )
    }
}

/** The actual burndown (a stepped line from the period's open count) over a dashed ideal line, drawn left to right up to [progress]. */
@Composable
private fun BurndownChart(recap: Recap, hud: Hud, progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        val burndown: Burndown = recap.burndown
        val scope = burndown.actual.first().open.coerceAtLeast(1)
        val span = (recap.range.end.toEpochMilli() - recap.range.start.toEpochMilli()).toFloat()
        fun x(at: java.time.Instant) = (at.toEpochMilli() - recap.range.start.toEpochMilli()) / span * size.width
        fun y(open: Int) = size.height * (1f - open.toFloat() / scope)
        val stroke = 3.dp.toPx()
        drawLine(JuiceColors.white(0.2f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        clipRect(right = size.width * progress) {
            val ideal = burndown.ideal
            drawLine(
                JuiceColors.white(0.55f),
                Offset(x(ideal.first().at), y(ideal.first().open)),
                Offset(x(ideal.last().at), y(ideal.last().open)),
                2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
            )
            val path = Path()
            val first = burndown.actual.first()
            path.moveTo(x(first.at), y(first.open))
            var level = first.open
            burndown.actual.drop(1).forEach { point ->
                path.lineTo(x(point.at), y(level))
                path.lineTo(x(point.at), y(point.open))
                level = point.open
            }
            // Open items stay flat to the end of the period.
            path.lineTo(size.width, y(level))
            drawPath(path, hud.accent, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
