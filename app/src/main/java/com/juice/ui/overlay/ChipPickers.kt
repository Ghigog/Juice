package com.juice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.juice.R
import com.juice.core.intake.ChipKind
import com.juice.core.routine.Cadence
import com.juice.feedback.FeedbackEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val DEADLINE_DAYS = 7
private val INTERVAL_WEEKS = 1..4
private val INTERVAL_DAYS = 2..6

/** The Repeat picker's options: every day, every 2..6 days, every week. */
internal val REPEAT_CADENCES: List<Cadence> =
    listOf(Cadence(DayOfWeek.values().toSet())) + INTERVAL_DAYS.map { Cadence(DayOfWeek.values().toSet(), intervalDays = it) }

/** The picker for the chip [OverlayShellState.chipPicker] names, if one is open; choosing applies it and closes it, tapping outside just closes it. */
@Composable
fun ChipPickers(
    state: OverlayShellState,
    hud: Hud,
    actions: OverlayActions = OverlayActions(),
    today: LocalDate = state.today,
    locale: Locale = Locale.getDefault(),
) {
    RepeatPicker(state, hud, actions, today)
    val picker = state.chipPicker ?: return
    val item = state.items.firstOrNull { it.id == picker.itemId } ?: return
    val span = picker.span
    Dialog(onDismissRequest = state::dismissChipPicker) {
        PickerPanel {
                when (span.kind) {
                    ChipKind.Deadline -> {
                        Title(stringResource(R.string.chip_picker_deadline))
                        for (offset in 0 until DEADLINE_DAYS) {
                            val day = today.plusDays(offset.toLong())
                            Option(hud, actions, day.dayOfWeek.label(locale), "chipPickerDay$offset") { state.setDeadlineDay(item, span, day) }
                        }
                    }
                    ChipKind.Interval -> {
                        Title(stringResource(R.string.chip_picker_interval))
                        for (days in INTERVAL_DAYS) {
                            Option(hud, actions, pluralStringResource(R.plurals.chip_picker_days, days, days), "chipPickerDays$days") {
                                state.setCadenceIntervalDays(item, span, days)
                            }
                        }
                        for (weeks in INTERVAL_WEEKS) {
                            Option(hud, actions, pluralStringResource(R.plurals.chip_picker_weeks, weeks, weeks), "chipPickerWeeks$weeks") {
                                state.setCadenceInterval(item, span, weeks)
                            }
                        }
                    }
                    ChipKind.Weekday -> {
                        Title(stringResource(R.string.chip_picker_weekday))
                        for (weekday in DayOfWeek.values()) {
                            Option(hud, actions, weekday.label(locale), "chipPickerWeekday${weekday.name}") { state.setCadenceWeekday(item, span, weekday) }
                        }
                    }
                    // A verb chip has nothing to pick.
                    ChipKind.Verb -> Unit
                }
        }
    }
}

/** The Repeat picker for a Routine with no cadence chip: choosing sets its cadence and closes it, tapping outside just closes it. */
@Composable
private fun RepeatPicker(state: OverlayShellState, hud: Hud, actions: OverlayActions, today: LocalDate) {
    val item = state.items.firstOrNull { it.id == state.repeatPickerItemId } ?: return
    Dialog(onDismissRequest = state::dismissRepeatPicker) {
        PickerPanel {
                Title(stringResource(R.string.repeat_picker_title))
                for (cadence in REPEAT_CADENCES) {
                    val days = cadence.intervalDays
                    val label = if (days == 1) stringResource(R.string.repeat_picker_day) else pluralStringResource(R.plurals.repeat_picker_days, days, days)
                    Option(hud, actions, label, "repeatPickerDays$days") { state.setCadence(item, cadence) }
                }
                Option(hud, actions, stringResource(R.string.repeat_picker_week), "repeatPickerWeek") {
                    state.setCadence(item, Cadence(setOf(today.dayOfWeek), item.cadence?.time))
            }
        }
    }
}

private fun DayOfWeek.label(locale: Locale) = getDisplayName(TextStyle.FULL, locale)

/** The glass card the pickers sit in: a dark well behind white 14% glass with a 1 dp white border, as the centre card. */
@Composable
private fun PickerPanel(content: @Composable () -> Unit) {
    Box(Modifier.roundedClip(JuiceShapes.Card).background(JuiceColors.Sheet)) {
        Column(
            Modifier
                .background(JuiceColors.white(0.14f))
                .border(1.dp, JuiceColors.white(0.28f), JuiceShapes.Card)
                .padding(16.dp),
        ) { content() }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        fontSize = JuiceType.SheetHeading,
        lineHeight = JuiceType.SheetHeadingLine,
        fontFamily = SpaceGrotesk,
        color = Color.White,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).testTag(TAG_CHIP_PICKER_TITLE),
    )
}

/** A Juice pill option; it fills with the Hud accent while pressed, and choosing it squeezes (SNAP) first. */
@Composable
private fun Option(hud: Hud, actions: OverlayActions, label: String, tag: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .touchTarget({
                actions.feedback(FeedbackEvent.SNAP)
                onClick()
            }, interactionSource = source),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .roundedClip(JuiceShapes.Pill)
                .background(if (pressed) hud.accent else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = JuiceType.Button, fontWeight = FontWeight.Bold, fontFamily = SpaceGrotesk, color = Color.White)
        }
    }
}

const val TAG_CHIP_PICKER_TITLE = "chipPickerTitle"
