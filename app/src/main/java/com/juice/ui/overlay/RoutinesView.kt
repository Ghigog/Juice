package com.juice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import kotlinx.coroutines.delay

const val TAG_ROUTINES_SCREEN = "routines_screen"
const val TAG_ROUTINES_CLOSE = "routines_close"
const val TAG_ROUTINES_EMPTY = "routines_empty"

/**
 * Every Active Routine on any day, in the user's card order, on the same carousel and centre card as the
 * main view. [state] is the Routines list: ✓ completes a Routine and ✗ archives it, each with an Undo.
 */
@Composable
internal fun RoutinesView(
    state: OverlayShellState,
    hud: Hud,
    settings: JuiceSettings,
    actions: OverlayActions,
    effects: OverlayEffects,
    reservoir: @Composable (Modifier) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var centre by remember { mutableStateOf(0) }
    val undo = state.undo
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(OverlayShellState.UNDO_WINDOW_MILLIS)
            state.expireUndo(undo)
        }
    }
    val closeLabel = stringResource(R.string.routines_close)
    Column(modifier.testTag(TAG_ROUTINES_SCREEN)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.routines_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(JuiceColors.white(0.14f))
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = closeLabel }
                    .testTag(TAG_ROUTINES_CLOSE),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.items.isEmpty()) {
            Text(
                stringResource(R.string.routines_empty), color = JuiceColors.white(0.75f),
                modifier = Modifier.weight(1f).testTag(TAG_ROUTINES_EMPTY),
            )
        } else {
            CardRow(state, hud, settings, actions, effects, reservoir, centre, onCentre = { centre = it }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            if (undo != null) UndoToast(undo, hud) {
                state.undoLast()
                actions.feedback(FeedbackEvent.UNDO)
            }
        }
    }
}
