package com.juice.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.selected
import com.juice.core.category.Category
import com.juice.core.category.CategoryPalette
import com.juice.core.category.PaletteColour
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.juice.R

const val TAG_CATEGORY_NAME_FIELD = "category_name_field"
const val TAG_CATEGORY_NAME_INPUT = "category_name_input"
const val TAG_CATEGORY_NAME_SAVE = "category_name_save"
const val TAG_CATEGORY_NAME_CANCEL = "category_name_cancel"
const val TAG_CATEGORY_SWATCH_PREFIX = "category_swatch_"

/**
 * The glass field for naming a new category or renaming one ([naming]). It rises in from below on a spring, or
 * simply fades in with reduce motion, and takes focus with the keyboard up.
 */
@Composable
internal fun CategoryNameField(
    naming: CategoryNaming,
    hud: Hud,
    reduceMotion: Boolean,
    onSave: (name: String, colour: Int?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Plays the tap sound and haptic when a swatch is picked. */
    onPick: () -> Unit = {},
) {
    var name by remember(naming) { mutableStateOf(naming.renaming?.name.orEmpty()) }
    var picked by remember(naming) { mutableStateOf(naming.colour) }
    val mainName = naming.renaming?.id == Category.MAIN_ID
    val accent = picked?.let { Color(it.accent) } ?: hud.accent
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
        if (reduceMotion) shown.animateTo(1f, tween(150))
        else shown.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    val shape = JuiceShapes.Input
    val title = stringResource(if (naming.renaming == null) R.string.category_new_title else R.string.category_rename_title)
    val fieldLabel = stringResource(R.string.category_name_field)
    Box(modifier.testTag(TAG_CATEGORY_NAME_FIELD), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = shown.value.coerceIn(0f, 1f)
                    if (!reduceMotion) translationY = (1f - shown.value) * 160.dp.toPx()
                }
                .clip(JuiceShapes.Card)
                .background(JuiceColors.white(0.14f))
                .border(1.dp, JuiceColors.white(0.25f), JuiceShapes.Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = JuiceType.SheetHeading, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                readOnly = mainName,
                textStyle = LocalTextStyle.current.copy(fontSize = JuiceType.Chat, color = Color.White),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(name, picked?.accent) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = fieldLabel }.testTag(TAG_CATEGORY_NAME_INPUT),
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(shape)
                            .background(JuiceColors.white(0.14f))
                            .border(1.dp, JuiceColors.white(0.25f), shape)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) Text(stringResource(R.string.category_name_hint), fontSize = JuiceType.Chat, color = JuiceColors.white(0.6f))
                        field()
                    }
                },
            )
            if (naming.renaming != null) {
                Swatches(picked, onPick = { picked = it; onPick() })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.category_cancel), JuiceColors.white(0.14f), Color.White, Modifier.weight(1f).testTag(TAG_CATEGORY_NAME_CANCEL), onCancel)
                PillButton(stringResource(R.string.category_save), accent, JuiceColors.Ink, Modifier.weight(1f).testTag(TAG_CATEGORY_NAME_SAVE)) { onSave(name, picked?.accent) }
            }
        }
    }
}

/** The palette as a row of juice dots, each a [MinTouchTarget] tap area; the [selected] one is ringed in white. */
@Composable
private fun Swatches(selected: PaletteColour?, onPick: (PaletteColour) -> Unit) {
    val label = stringResource(R.string.category_colour_label)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.SpaceBetween) {
        for (colour in CategoryPalette.colours) {
            val on = colour == selected
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .testTag(TAG_CATEGORY_SWATCH_PREFIX + colour.accent)
                    .semantics { this.selected = on }
                    .touchTarget({ onPick(colour) }),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(colour.accent), Color(colour.deep))))
                        .then(if (on) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                )
            }
        }
    }
}
