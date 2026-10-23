package com.juice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R

const val TAG_CREATOR = "creator"
const val TAG_CREATOR_INPUT = "creator_input"
const val TAG_CREATOR_CONFIRM = "creator_confirm"
const val TAG_CREATOR_CANCEL = "creator_cancel"
const val TAG_CREATOR_PROGRESS = "creator_progress"
const val TAG_CREATOR_ERROR = "creator_error"

/** The creator card opened: a hobby to type and confirm, progress while the coach is made, the error if that failed. */
@Composable
internal fun CreatorFlow(creator: CreatorState, hud: Hud, onConfirm: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var hobby by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.testTag(TAG_CREATOR), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.creator_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = hobby,
            onValueChange = { hobby = it },
            enabled = !creator.busy,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().testTag(TAG_CREATOR_INPUT),
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
                    if (hobby.isEmpty()) Text(stringResource(R.string.creator_hint), fontSize = 15.sp, color = JuiceColors.white(0.6f))
                    field()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        if (creator.busy) Text(stringResource(R.string.creator_progress), Modifier.testTag(TAG_CREATOR_PROGRESS), fontSize = 15.sp)
        creator.error?.let {
            Text(stringResource(R.string.creator_error, it), Modifier.testTag(TAG_CREATOR_ERROR), fontSize = 15.sp, color = JuiceColors.white(0.8f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.coach_cancel), JuiceColors.white(0.14f), Color.White, Modifier.weight(1f).testTag(TAG_CREATOR_CANCEL)) {
                if (!creator.busy) onCancel()
            }
            PillButton(stringResource(R.string.creator_confirm), hud.accent, JuiceColors.Ink, Modifier.weight(1f).testTag(TAG_CREATOR_CONFIRM)) {
                onConfirm(hobby)
            }
        }
    }
}
