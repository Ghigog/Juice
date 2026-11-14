package com.juice.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.juice.R
import com.juice.RegisterComposeActivity
import com.juice.ui.overlay.ENTRANCE_TOTAL_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Onboarding shows its copy under tags, Continue fires, and the entrance runs unless reduce motion is on. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class OnboardingScreenTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun show(reduceMotion: Boolean, onContinue: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent { OnboardingScreen(onContinue, accent = Color(0xFFFFB300), reduceMotion = reduceMotion) }
    }

    @Test
    fun copyShowsUnderEachTag() {
        show(reduceMotion = true)
        compose.onNodeWithTag(TAG_ONBOARDING_TITLE).assertTextEquals(context.getString(R.string.onboarding_title))
        compose.onNodeWithTag(TAG_ONBOARDING_DISCLOSURE)
            .assertTextEquals(context.getString(R.string.onboarding_cloud_disclosure))
        compose.onNodeWithTag(TAG_ONBOARDING_CONTINUE)
            .assertTextEquals(context.getString(R.string.onboarding_continue))
    }

    @Test
    fun continueInvokesOnContinue() {
        var taps = 0
        show(reduceMotion = true) { taps++ }
        compose.onNodeWithTag(TAG_ONBOARDING_CONTINUE).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun withReduceMotionTheContentIsInPlaceOnTheFirstFrame() {
        show(reduceMotion = true)
        compose.mainClock.advanceTimeByFrame()
        val first = compose.onNodeWithTag(TAG_ONBOARDING_CONTINUE).getBoundsInRoot()
        compose.mainClock.advanceTimeBy(ENTRANCE_TOTAL_MILLIS.toLong())
        assertEquals(first, compose.onNodeWithTag(TAG_ONBOARDING_CONTINUE).getBoundsInRoot())
    }

    @Test
    fun withoutReduceMotionTheContentRisesIntoPlace() {
        show(reduceMotion = false)
        compose.mainClock.advanceTimeByFrame()
        val first = compose.onNodeWithTag(TAG_ONBOARDING_CONTINUE).getBoundsInRoot()
        compose.mainClock.advanceTimeBy(ENTRANCE_TOTAL_MILLIS.toLong())
        assertNotEquals(first, compose.onNodeWithTag(TAG_ONBOARDING_CONTINUE).getBoundsInRoot())
    }
}
