package com.juice.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.intake.ChipSpan
import com.juice.core.intake.ItemType
import com.juice.core.intake.RulesFallback
import com.juice.core.model.Item
import com.juice.core.model.Step

/** A chip in the title: [span] with its text offsets [start] until [end] (end exclusive). */
internal class TitleChip(val span: ChipSpan, val start: Int, val end: Int)

/** The chips of [item]'s title; none unless the rules still sort the title as the card's type. */
internal fun titleChips(item: Item): List<TitleChip> {
    if (RulesFallback.classify(item.title).type != item.type) return emptyList()
    // Spans are measured on the trimmed title.
    val lead = item.title.length - item.title.trimStart().length
    return RulesFallback.spansOf(item.title, item.type).map { TitleChip(it, it.range.first + lead, it.range.last + 1 + lead) }
}

internal fun titleText(title: String, chips: List<TitleChip>) = buildAnnotatedString {
    var at = 0
    chips.sortedBy { it.start }.forEach { chip ->
        append(title.substring(at, chip.start))
        withStyle(SpanStyle(background = JuiceColors.white(0.24f))) { append(title.substring(chip.start, chip.end)) }
        at = chip.end
    }
    append(title.substring(at))
}

/** Below this height, and at least [WIDE_CARD_MIN_WIDTH] wide, the centre card lays out in two columns. */
internal val WIDE_CARD_MAX_HEIGHT = 300.dp
internal val WIDE_CARD_MIN_WIDTH = 480.dp

/** The width of the wide card's right column: the worth, the buttons and the hint. */
internal val WIDE_CARD_SIDE = 240.dp

/** One step of a Project; a done step stays listed, struck through. */
@Composable
internal fun StepRow(step: Step, modifier: Modifier = Modifier) {
    Text(
        (if (step.done) "✓ " else "○ ") + step.title,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = JuiceColors.white(if (step.done) 0.5f else 0.92f),
        textDecoration = if (step.done) TextDecoration.LineThrough else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().height(36.dp).wrapContentHeight(Alignment.CenterVertically).testTag(stepTag(step.id)),
    )
}

internal fun stepTag(id: String) = "step_$id"

@Composable
internal fun ParsingChip(onDeviceOnly: Boolean) {
    val shimmer by rememberInfiniteTransition(label = "parse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "shimmer",
    )
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    0f to JuiceColors.white(0.1f), shimmer to JuiceColors.white(0.3f), 1f to JuiceColors.white(0.1f),
                ),
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (onDeviceOnly) R.string.overlay_parsing_local else R.string.overlay_parsing_cloud),
            fontSize = 11.sp,
            color = JuiceColors.white(0.9f),
        )
    }
}

@Composable
internal fun TitleField(title: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    var text by remember(title) { mutableStateOf(title) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        singleLine = false,
        textStyle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JuiceColors.white(0.1f))
            .padding(8.dp)
            .focusRequester(focus)
            .testTag(TAG_TITLE_FIELD),
    )
}

@Composable
internal fun PillButton(label: String, background: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun metaOf(state: OverlayShellState, item: Item): String = when {
    item.type == ItemType.List -> state.stepsOf(item).size.let { left ->
        "No deadline. $left ${if (left == 1) "task" else "tasks"} left."
    }
    item.type.hasSteps -> state.stepsOf(item).size.let { left ->
        if (left == 0) "All steps done. Tick it off." else "$left ${if (left == 1) "step" else "steps"} to go"
    }
    item.type == ItemType.Routine && item.cadence != null -> item.cadence!!.label() +
        (item.deadline?.let { " · Due ${deadlineLabel(it)}" } ?: "")
    item.deadline != null -> "Due ${deadlineLabel(item.deadline!!)}"
    item.type == ItemType.Note -> "No deadline. Notes are quick catches."
    else -> "Add a time to make it a Goal."
}

internal fun worthOf(item: Item, hud: Hud): String {
    val m = hud.multiplierLabel
    return if (item.type.hasSteps) "${hud.baseXp(item.type)} x $m on finish"
    else "${hud.baseXp(item.type)} x $m = ${hud.worth(item.type)} Juice"
}

/** One row of the type pill's drop-down: type colour dot, small-caps name, and a check on the current type. */
@Composable
internal fun TypeMenuRow(type: ItemType, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(JuiceColors.of(type)))
                Text(
                    type.name.uppercase(),
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                if (current) Text("✓", color = JuiceColors.of(type), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (current) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .semantics { selected = current },
    )
}
