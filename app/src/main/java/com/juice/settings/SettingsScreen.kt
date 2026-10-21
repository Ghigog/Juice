package com.juice.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juice.R
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: JuiceSettings, onChange: ((JuiceSettings) -> JuiceSettings) -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_title))
        SwitchRow(R.string.settings_sound, settings.sound) { v -> onChange { it.copy(sound = v) } }
        SwitchRow(R.string.settings_tap_buttons, settings.tapButtons) { v -> onChange { it.copy(tapButtons = v) } }
        SwitchRow(R.string.settings_haptics, settings.haptics) { v -> onChange { it.copy(haptics = v) } }
        SwitchRow(R.string.settings_reduce_motion, settings.reduceMotion) { v -> onChange { it.copy(reduceMotion = v) } }
        SwitchRow(R.string.settings_blur, settings.blur) { v -> onChange { it.copy(blur = v) } }
        SwitchRow(R.string.settings_on_device_only, settings.onDeviceOnly) { v -> onChange { it.copy(onDeviceOnly = v) } }
        Text(stringResource(R.string.settings_strip_width, settings.stripWidthPercent))
        Slider(
            value = settings.stripWidthPercent.toFloat(),
            onValueChange = { v -> onChange { it.copy(stripWidthPercent = v.roundToInt()) } },
            valueRange = JuiceSettings.MIN_STRIP_WIDTH_PERCENT.toFloat()..JuiceSettings.MAX_STRIP_WIDTH_PERCENT.toFloat(),
        )
        Text(stringResource(R.string.settings_strip_position))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StripPosition.entries.forEach { position ->
                Button(
                    enabled = settings.stripPosition != position,
                    onClick = { onChange { it.copy(stripPosition = position) } },
                ) {
                    Text(
                        stringResource(
                            if (position == StripPosition.Top) R.string.settings_strip_top
                            else R.string.settings_strip_bottom,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
