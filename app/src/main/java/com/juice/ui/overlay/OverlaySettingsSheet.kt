package com.juice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.core.progression.Flavour
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import com.juice.ui.theme.FlavourTheme

/** Comfort switches and the flavour picker, inside the overlay. */
@Composable
internal fun SettingsSheet(
    settings: JuiceSettings,
    hud: Hud,
    actions: OverlayActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = actions.onSettingsChange
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(JuiceColors.Sheet)
            .border(1.dp, JuiceColors.white(0.2f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(JuiceColors.white(0.14f))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 16.dp)
                    .testTag(TAG_SETTINGS_DONE),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.overlay_done), fontSize = 13.sp) }
        }
        Toggle(R.string.settings_sound, R.string.settings_sound_note, settings.sound, hud) { v -> change { it.copy(sound = v) } }
        Toggle(R.string.settings_haptics, R.string.settings_haptics_note, settings.haptics, hud) { v -> change { it.copy(haptics = v) } }
        Toggle(R.string.settings_reduce_motion, R.string.settings_reduce_motion_note, settings.reduceMotion, hud) { v -> change { it.copy(reduceMotion = v) } }
        Toggle(R.string.settings_blur, R.string.settings_blur_note, settings.blur, hud) { v -> change { it.copy(blur = v) } }
        Toggle(R.string.settings_tap_buttons, R.string.settings_tap_buttons_note, settings.tapButtons, hud) { v -> change { it.copy(tapButtons = v) } }
        Toggle(R.string.settings_on_device_only, R.string.settings_on_device_only_note, settings.onDeviceOnly, hud) { v -> change { it.copy(onDeviceOnly = v) } }
        Text(
            stringResource(R.string.settings_flavour).uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            color = JuiceColors.white(0.7f),
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )
        Flavour.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { flavour ->
                    FlavourTile(flavour, selected = hud.flavour == flavour, unlocked = flavour.unlockLevel <= hud.level, Modifier.weight(1f)) {
                        change { it.copy(flavour = flavour) }
                        actions.feedback(FeedbackEvent.CAPTURE)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.settings_flavour_note),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = JuiceColors.white(0.7f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Toggle(label: Int, note: Int, checked: Boolean, hud: Hud, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), fontSize = 15.sp)
            Text(stringResource(note), fontSize = 12.sp, color = JuiceColors.white(0.7f))
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = hud.accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = JuiceColors.white(0.2f),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(JuiceColors.white(0.1f)))
}

@Composable
private fun FlavourTile(flavour: Flavour, selected: Boolean, unlocked: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val theme = FlavourTheme.of(flavour)
    val shadow = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f))
    Column(
        modifier
            .height(76.dp)
            .alpha(if (unlocked) 1f else 0.45f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(theme.accent), Color(theme.deep))))
            .border(2.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(enabled = unlocked, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(TAG_FLAVOUR_PREFIX + flavour.name),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(flavour.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = shadow)
        Spacer(Modifier.height(2.dp))
        Text(
            if (unlocked) stringResource(R.string.settings_flavour_level, flavour.unlockLevel)
            else stringResource(R.string.settings_flavour_locked, flavour.unlockLevel),
            fontSize = 12.sp,
            style = shadow,
        )
    }
}
