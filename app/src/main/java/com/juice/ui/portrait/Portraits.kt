package com.juice.ui.portrait

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.juice.R
import com.juice.core.character.Characters
import com.juice.core.character.Emotion

/** The shared placeholder art, one drawable per emotion. */
private val placeholders: Map<Emotion, Int> = mapOf(
    Emotion.NEUTRAL to R.drawable.portrait_placeholder_neutral,
    Emotion.HAPPY to R.drawable.portrait_placeholder_happy,
    Emotion.STERN to R.drawable.portrait_placeholder_stern,
    Emotion.THINKING to R.drawable.portrait_placeholder_thinking,
)

/** Every character's portraits by emotion. Replace a character's entry here with real art; unlisted ones use the placeholders. */
private val table: Map<String, Map<Emotion, Int>> =
    Characters.all.associate { it.id to placeholders }

object Portraits {
    /** The portrait of [characterId] showing [emotion]; an unknown character gets the placeholder. */
    @DrawableRes
    fun res(characterId: String, emotion: Emotion): Int =
        (table[characterId] ?: placeholders).getValue(emotion)

    @StringRes
    fun description(emotion: Emotion): Int = when (emotion) {
        Emotion.NEUTRAL -> R.string.portrait_neutral
        Emotion.HAPPY -> R.string.portrait_happy
        Emotion.STERN -> R.string.portrait_stern
        Emotion.THINKING -> R.string.portrait_thinking
    }
}

@Composable
fun CoachPortrait(characterId: String, emotion: Emotion, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Portraits.res(characterId, emotion)),
        contentDescription = stringResource(Portraits.description(emotion)),
        modifier = modifier,
    )
}
