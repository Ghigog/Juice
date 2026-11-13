package com.juice.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R

/** Tells the host whether the shell is in writing mode and lets it end that mode, for the BACK key. */
class WritingBack {
    internal var isWriting: () -> Boolean = { false }
    internal var leave: () -> Unit = {}
    internal var isRecapOpen: () -> Boolean = { false }
    internal var closeRecap: () -> Unit = {}
    internal var isRoutinesOpen: () -> Boolean = { false }
    internal var closeRoutines: () -> Unit = {}

    /** BACK closes the Recap or the Routines view while one is open, then ends writing mode (keeping the draft), and otherwise calls [close]. */
    fun back(close: () -> Unit) = when {
        isRecapOpen() -> closeRecap()
        isRoutinesOpen() -> closeRoutines()
        isWriting() -> leave()
        else -> close()
    }
}

@Composable
internal fun Capture(
    hud: Hud,
    draft: String,
    onDraftChange: (String) -> Unit,
    writing: Boolean,
    onWritingChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier,
    onSubmit: (String) -> Unit,
) {
    fun submit() {
        if (draft.isBlank()) return
        onSubmit(draft)
        onDraftChange("")
        onLeave()
    }
    val shape = if (writing) JuiceShapes.Pill else JuiceShapes.Input
    Row(
        modifier.fillMaxWidth().then(if (writing) Modifier else Modifier.height(56.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            singleLine = !writing,
            textStyle = LocalTextStyle.current.copy(fontSize = if (writing) JuiceType.SheetHeading else JuiceType.Chat),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = if (writing) ImeAction.Default else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { onWritingChange(it.isFocused) }
                .testTag(TAG_CAPTURE_FIELD),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(JuiceColors.white(0.14f))
                        .border(1.dp, JuiceColors.white(0.25f), shape)
                        .padding(horizontal = 20.dp, vertical = if (writing) 18.dp else 0.dp),
                    contentAlignment = if (writing) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) Text(stringResource(R.string.overlay_capture_hint), fontSize = if (writing) JuiceType.SheetHeading else JuiceType.Chat, color = JuiceColors.white(0.6f))
                    field()
                }
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (writing) {
                Spacer(Modifier.weight(1f))
            }
        val label = stringResource(R.string.overlay_add)
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(hud.accent)
                .clickable { submit() }
                .semantics { contentDescription = label }
                .testTag(TAG_ADD),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val w = 2.6.dp.toPx()
                val u = size.width / 24f
                drawPath(
                    Path().apply { moveTo(12 * u, 5 * u); lineTo(12 * u, 19 * u); moveTo(5 * u, 12 * u); lineTo(19 * u, 12 * u) },
                    JuiceColors.Ink,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        }
    }
}
