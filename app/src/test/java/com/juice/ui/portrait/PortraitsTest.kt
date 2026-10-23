package com.juice.ui.portrait

import com.juice.core.character.Characters
import com.juice.core.character.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PortraitsTest {
    @Test
    fun everyCharacterAndEmotionResolvesToADrawable() {
        for (character in Characters.all) for (emotion in Emotion.entries) {
            assertNotEquals(0, Portraits.res(character.id, emotion))
        }
    }

    @Test
    fun theFourEmotionsResolveToFourDifferentDrawables() {
        val ids = Emotion.entries.map { Portraits.res(Characters.Jim.id, it) }
        assertEquals(4, ids.toSet().size)
    }

    @Test
    fun anUnknownCharacterGetsThePlaceholderForTheEmotion() {
        for (emotion in Emotion.entries) {
            assertEquals(Portraits.res(Characters.Jim.id, emotion), Portraits.res("nobody", emotion))
        }
    }
}
