package com.juice.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import com.juice.RegisterComposeActivity
import com.juice.feedback.FeedbackEvent
import com.juice.intake.KeyCheck
import com.juice.settings.JuiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GroqKeyRowTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val masked = MutableStateFlow<String?>(null)
    private val events = mutableListOf<FeedbackEvent>()
    private var result = KeyCheck.Works
    private val actions = OverlayActions(
        feedback = { events += it },
        groqKeyMasked = masked,
        onSaveGroqKey = { key, onChecked -> masked.value = key.masked; onChecked(result) },
        onRemoveGroqKey = { masked.value = null },
    )

    @Composable
    private fun Sheet() {
        val saved by masked.collectAsState()
        SettingsSheet(JuiceSettings(onDeviceOnly = false), Hud(), actions, groqKeySaved = saved != null, onDone = {})
    }

    private fun saveKey() {
        compose.onNodeWithTag(TAG_GROQ_KEY_FIELD).performTextInput("abcd_secret1234")
        compose.onNodeWithTag(TAG_GROQ_KEY_SAVE).assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun withoutAKeyOnDeviceOnlyIsLockedWithANote() {
        compose.setContent { Sheet() }
        compose.onNodeWithText("Paste your Groq key to unlock").assertIsDisplayed()
        compose.onNodeWithText("On-device only").assertIsNotEnabled()
        compose.onNodeWithTag(TAG_GROQ_KEY_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_GROQ_KEY_SAVE).assertIsNotEnabled()
    }

    @Test
    fun savingShowsTheMaskedKeyAndUnlocksTheToggle() {
        compose.setContent { Sheet() }
        saveKey()
        compose.onNodeWithTag(TAG_GROQ_KEY_MASKED).assertTextEquals("abcd…1234")
        compose.onNodeWithTag(TAG_GROQ_KEY_NOTE).assertTextEquals("Key works")
        compose.onNodeWithText("On-device only").assertIsEnabled()
        assertEquals(listOf(FeedbackEvent.CAPTURE), events)
    }

    @Test
    fun aRejectedKeyShowsACalmNote() {
        result = KeyCheck.Rejected
        compose.setContent { Sheet() }
        saveKey()
        compose.onNodeWithTag(TAG_GROQ_KEY_NOTE).assertTextEquals("Groq rejected this key")
    }

    @Test
    fun anUnreachableGroqSavesAnyway() {
        result = KeyCheck.Unreachable
        compose.setContent { Sheet() }
        saveKey()
        compose.onNodeWithTag(TAG_GROQ_KEY_NOTE).assertTextEquals("Couldn't reach Groq, saved anyway")
    }

    @Test
    fun removingTheKeyRelocksTheToggle() {
        compose.setContent { Sheet() }
        saveKey()
        compose.onNodeWithTag(TAG_GROQ_KEY_REMOVE).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_GROQ_KEY_FIELD).assertIsDisplayed()
        compose.onNodeWithText("Paste your Groq key to unlock").assertIsDisplayed()
        compose.onNodeWithText("On-device only").assertIsNotEnabled()
    }
}
