package com.juice.ui.typing

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

/**
 * Types [text] out letter by letter; a tap shows the rest at once. [onLetter] is called for each
 * newly revealed non-whitespace letter, never for skipped ones. Keyed by [text], so recomposition
 * does not restart a reveal and a finished one stays finished.
 */
@Composable
fun TypewriterText(
    text: String,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
    delayMs: Long = TypewriterState.DEFAULT_DELAY_MS,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = TextStyle.Default,
) {
    val state = remember(text) { TypewriterState(text, delayMs) }
    var revealed by remember(state) { mutableIntStateOf(state.revealed) }
    val currentOnLetter by rememberUpdatedState(onLetter)

    LaunchedEffect(state) {
        while (!state.done) {
            delay(state.delayMs)
            val letter = state.advance() ?: break
            revealed = state.revealed
            if (!letter.isWhitespace()) currentOnLetter(letter)
        }
    }

    Text(
        text.substring(0, revealed),
        modifier = modifier.clickable(enabled = revealed < text.length) {
            state.skip()
            revealed = state.revealed
        },
        color = color,
        fontSize = fontSize,
        style = style,
    )
}
