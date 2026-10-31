package com.juice.ui.overlay

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.juice.RegisterComposeActivity
import com.juice.feedback.FeedbackEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class ShakeRewardTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    @Test fun onlyAMeterThatReadsAHundredIsFull() {
        assertTrue(shakeArmed(1f))
        assertTrue(shakeArmed(0.996f))
        assertFalse(shakeArmed(0.99f))
        assertFalse(shakeArmed(0.5f))
    }

    @Test fun aFullMeterPromptsToShakeAndNinetyNineDoesNot() {
        var fill by mutableStateOf(1f)
        compose.setContent { ShakeReward(Hud(fill = fill), reduceMotion = false, OverlayActions(), Modifier.size(300.dp), Random(1)) }
        compose.onNodeWithTag(TAG_SHAKE_PROMPT).assertIsDisplayed()
        fill = 0.99f
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_SHAKE_PROMPT).assertDoesNotExist()
    }

    @Test fun reduceMotionHidesThePrompt() {
        compose.setContent { ShakeReward(Hud(fill = 1f), reduceMotion = true, OverlayActions(), Modifier.size(300.dp), Random(1)) }
        compose.onNodeWithTag(TAG_SHAKE_PROMPT).assertDoesNotExist()
    }

    @Test fun shakingHardEnoughBurstsTheJuiceOnce() {
        var erupted = 0
        val played = mutableListOf<FeedbackEvent>()
        val actions = OverlayActions(feedback = { played += it }, shake = { 3f }, onErupt = { erupted++ })
        compose.mainClock.autoAdvance = false
        compose.setContent { ShakeReward(Hud(fill = 1f), reduceMotion = false, actions, Modifier.size(300.dp), Random(1)) }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0, erupted)
        compose.onNodeWithTag(TAG_SHAKE_PROMPT).assertExists()
        compose.mainClock.advanceTimeBy(2500)
        assertEquals(1, erupted)
        assertEquals(listOf(FeedbackEvent.SATIATED_PEAK), played)
        compose.onNodeWithTag(TAG_SHAKE_PROMPT).assertDoesNotExist()
    }
}
