package com.juice.ui.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.juice.R
import com.juice.core.intake.ChipKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val DEADLINE_DAYS = 7
private val INTERVAL_WEEKS = 1..4

/** The picker for the chip [OverlayShellState.chipPicker] names, if one is open; choosing applies it and closes it, tapping outside just closes it. */
@Composable
fun ChipPickers(state: OverlayShellState, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()) {
    val picker = state.chipPicker ?: return
    val item = state.items.firstOrNull { it.id == picker.itemId } ?: return
    val span = picker.span
    Dialog(onDismissRequest = state::dismissChipPicker) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                when (span.kind) {
                    ChipKind.Deadline -> {
                        Text(stringResource(R.string.chip_picker_deadline), style = MaterialTheme.typography.titleMedium)
                        for (offset in 0 until DEADLINE_DAYS) {
                            val day = today.plusDays(offset.toLong())
                            Option(day.dayOfWeek.label(locale), "chipPickerDay$offset") { state.setDeadlineDay(item, span, day) }
                        }
                    }
                    ChipKind.Interval -> {
                        Text(stringResource(R.string.chip_picker_interval), style = MaterialTheme.typography.titleMedium)
                        for (weeks in INTERVAL_WEEKS) {
                            Option(pluralStringResource(R.plurals.chip_picker_weeks, weeks, weeks), "chipPickerWeeks$weeks") {
                                state.setCadenceInterval(item, span, weeks)
                            }
                        }
                    }
                    ChipKind.Weekday -> {
                        Text(stringResource(R.string.chip_picker_weekday), style = MaterialTheme.typography.titleMedium)
                        for (weekday in DayOfWeek.values()) {
                            Option(weekday.label(locale), "chipPickerWeekday${weekday.name}") { state.setCadenceWeekday(item, span, weekday) }
                        }
                    }
                    // A verb chip has nothing to pick.
                    ChipKind.Verb -> Unit
                }
            }
        }
    }
}

private fun DayOfWeek.label(locale: Locale) = getDisplayName(TextStyle.FULL, locale)

@Composable
private fun Option(label: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth().testTag(tag)) { Text(label) }
}
