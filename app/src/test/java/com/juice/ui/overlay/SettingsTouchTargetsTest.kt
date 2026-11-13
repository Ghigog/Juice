package com.juice.ui.overlay

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.juice.RegisterComposeActivity
import com.juice.settings.JuiceSettings
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsTouchTargetsTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private fun open() = compose.setContent {
        SettingsSheet(JuiceSettings(onDeviceOnly = false), Hud(), OverlayActions(), groqKeySaved = false, onDone = {})
    }

    @Test fun doneIsAtLeast44TallAndClickable() {
        open()
        compose.onNodeWithTag(TAG_SETTINGS_DONE).assertHeightIsAtLeast(44.dp).assertHasClickAction()
    }

    @Test fun pillIsAtLeast44TallAndAnEnabledOneClicks() {
        open()
        compose.onNodeWithTag(TAG_GROQ_KEY_PASTE).assertHeightIsAtLeast(44.dp).assertHasClickAction()
    }

    @Test fun disabledPillIsStillTallButNotEnabled() {
        open()
        compose.onNodeWithTag(TAG_GROQ_KEY_SAVE).assertHeightIsAtLeast(44.dp).assertIsNotEnabled()
    }
}
