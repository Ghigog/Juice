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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.juice.core.intake.GroqKey
import com.juice.llm.KeyCheck
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
import com.juice.core.card.CardOrder
import com.juice.core.liquid.LiquidQuality
import com.juice.core.progression.Flavour
import com.juice.feedback.FeedbackEvent
import com.juice.settings.CharacterSettings
import com.juice.ui.settings.CharactersSection
import com.juice.settings.JuiceSettings
import com.juice.settings.SwipeDistance
import com.juice.ui.theme.FlavourTheme

const val TAG_CARD_ORDER_PREFIX = "card_order_"

/** Comfort switches and the flavour picker, inside the overlay. */
@Composable
internal fun SettingsSheet(
    settings: JuiceSettings,
    hud: Hud,
    actions: OverlayActions,
    groqKeySaved: Boolean = false,
    onDone: () -> Unit,
    onSwipeDistance: () -> Unit = {},
    characters: CharacterSettings? = null,
    modifier: Modifier = Modifier,
) {
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
        SettingsContent(settings, hud, actions, groqKeySaved, onSwipeDistance, characters)
    }
}

/** The settings rows shared by the overlay's sheet and the app's settings screen. */
@Composable
internal fun SettingsContent(
    settings: JuiceSettings,
    hud: Hud,
    actions: OverlayActions,
    groqKeySaved: Boolean,
    onSwipeDistance: () -> Unit = {},
    characters: CharacterSettings? = null,
) {
    val change = actions.onSettingsChange
    Column(Modifier.fillMaxWidth()) {
        Toggle(R.string.settings_sound, R.string.settings_sound_note, settings.sound, hud) { v -> change { it.copy(sound = v) } }
        Toggle(R.string.settings_haptics, R.string.settings_haptics_note, settings.haptics, hud) { v -> change { it.copy(haptics = v) } }
        Toggle(R.string.settings_reduce_motion, R.string.settings_reduce_motion_note, settings.reduceMotion, hud) { v -> change { it.copy(reduceMotion = v) } }
        Toggle(R.string.settings_blur, R.string.settings_blur_note, settings.blur, hud) { v -> change { it.copy(blur = v) } }
        Toggle(R.string.settings_tap_buttons, R.string.settings_tap_buttons_note, settings.tapButtons, hud) { v -> change { it.copy(tapButtons = v) } }
        Toggle(
            R.string.settings_on_device_only,
            if (groqKeySaved) R.string.settings_on_device_only_note else R.string.settings_on_device_only_locked,
            settings.onDeviceOnly || !groqKeySaved,
            hud,
            enabled = groqKeySaved,
        ) { v -> change { it.copy(onDeviceOnly = v) } }
        GroqKeyRow(actions, settings.reduceMotion)
        SectionLabel(R.string.settings_swipe_distance)
        ChoiceRow(SwipeDistance.entries, settings.swipeDistance, hud, { stringResource(it.label()) }, { TAG_SWIPE_DISTANCE_PREFIX + it.name }) { distance ->
            change { it.copy(swipeDistance = distance) }
            onSwipeDistance()
            actions.feedback(FeedbackEvent.CAPTURE)
        }
        SectionLabel(R.string.settings_card_order)
        ChoiceRow(CardOrder.entries, settings.cardOrder, hud, { stringResource(it.label()) }, { TAG_CARD_ORDER_PREFIX + it.name }) { order ->
            change { it.copy(cardOrder = order) }
            actions.feedback(FeedbackEvent.CAPTURE)
        }
        Text(
            stringResource(R.string.settings_card_order_note),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = JuiceColors.white(0.7f),
            modifier = Modifier.padding(top = 8.dp),
        )
        SectionLabel(R.string.settings_liquid_quality)
        ChoiceRow(LiquidQuality.entries, settings.liquidQuality, hud, { stringResource(it.label()) }, { TAG_LIQUID_QUALITY_PREFIX + it.name }) { quality ->
            change { it.copy(liquidQuality = quality) }
            actions.feedback(FeedbackEvent.CAPTURE)
        }
        Text(
            stringResource(R.string.settings_liquid_quality_note),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = JuiceColors.white(0.7f),
            modifier = Modifier.padding(top = 8.dp),
        )
        SectionLabel(R.string.settings_flavour)
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
        if (characters != null) {
            SectionLabel(R.string.settings_characters)
            CharactersSection(characters)
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        stringResource(text).uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        color = JuiceColors.white(0.7f),
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

/** A row of pill buttons, one per option, with [selected] filled in the flavour's accent. */
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    hud: Hud,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = selected == option
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) hud.accent else JuiceColors.white(0.14f))
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(option) }
                    .testTag(tag(option)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) JuiceColors.Ink else Color.White,
                )
            }
        }
    }
}

internal fun LiquidQuality.label(): Int = when (this) {
    LiquidQuality.FAST -> R.string.settings_liquid_fast
    LiquidQuality.BALANCED -> R.string.settings_liquid_balanced
    LiquidQuality.BEST -> R.string.settings_liquid_best
}

internal fun CardOrder.label(): Int = when (this) {
    CardOrder.Newest -> R.string.settings_card_order_newest
    CardOrder.Oldest -> R.string.settings_card_order_oldest
    CardOrder.Custom -> R.string.settings_card_order_custom
}

internal fun SwipeDistance.label(): Int = when (this) {
    SwipeDistance.Short -> R.string.settings_swipe_short
    SwipeDistance.Medium -> R.string.settings_swipe_medium
    SwipeDistance.Long -> R.string.settings_swipe_long
}

@Composable
private fun Toggle(label: Int, note: Int, checked: Boolean, hud: Hud, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
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
            enabled = enabled,
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

const val TAG_GROQ_KEY_FIELD = "groqKeyField"
const val TAG_GROQ_KEY_PASTE = "groqKeyPaste"
const val TAG_GROQ_KEY_SAVE = "groqKeySave"
const val TAG_GROQ_KEY_REMOVE = "groqKeyRemove"
const val TAG_GROQ_KEY_MASKED = "groqKeyMasked"
const val TAG_GROQ_KEY_NOTE = "groqKeyNote"

/** Paste, save and remove the person's Groq key; once saved only the masked key shows. */
@Composable
private fun GroqKeyRow(actions: OverlayActions, reduceMotion: Boolean) {
    val masked by actions.groqKeyMasked.collectAsState()
    var text by remember { mutableStateOf("") }
    var check by remember { mutableStateOf<KeyCheck?>(null) }
    val clipboard = LocalClipboardManager.current
    val squeeze = remember { Animatable(1f) }
    val parsed = GroqKey.parse(text)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).scale(squeeze.value)) {
        if (masked == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JuiceColors.white(0.1f))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.settings_groq_key_hint), fontSize = 14.sp, fontFamily = SpaceGrotesk, color = JuiceColors.white(0.7f))
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = SpaceGrotesk),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().testTag(TAG_GROQ_KEY_FIELD),
                    )
                }
                Pill(R.string.settings_groq_key_paste, TAG_GROQ_KEY_PASTE, enabled = true) {
                    clipboard.getText()?.text?.let { text = it; check = null }
                }
                Pill(R.string.settings_groq_key_save, TAG_GROQ_KEY_SAVE, enabled = parsed != null) {
                    val key = parsed ?: return@Pill
                    text = ""
                    actions.feedback(FeedbackEvent.CAPTURE)
                    actions.onSaveGroqKey(key) { check = it }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JuiceColors.white(0.1f))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(masked.orEmpty(), fontSize = 14.sp, fontFamily = SpaceGrotesk, modifier = Modifier.testTag(TAG_GROQ_KEY_MASKED)) }
                Pill(R.string.settings_groq_key_remove, TAG_GROQ_KEY_REMOVE, enabled = true) {
                    check = null
                    actions.onRemoveGroqKey()
                }
            }
        }
        val note = when (check) {
            KeyCheck.Works -> R.string.settings_groq_key_works
            KeyCheck.Rejected -> R.string.settings_groq_key_rejected
            KeyCheck.Unreachable -> R.string.settings_groq_key_unreachable
            null -> null
        }
        if (note != null && masked != null) {
            Text(
                stringResource(note),
                fontSize = 12.sp,
                color = JuiceColors.white(0.7f),
                modifier = Modifier.padding(top = 6.dp).testTag(TAG_GROQ_KEY_NOTE),
            )
        }
    }
    // A quick squeeze when a key lands; none with Reduce motion.
    LaunchedEffect(masked) {
        if (masked != null && !reduceMotion) {
            squeeze.animateTo(0.96f, tween(90))
            squeeze.animateTo(1f, tween(160))
        }
    }
}

@Composable
private fun Pill(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(18.dp))
            .background(JuiceColors.white(0.14f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(label), fontSize = 13.sp, fontFamily = SpaceGrotesk) }
}
