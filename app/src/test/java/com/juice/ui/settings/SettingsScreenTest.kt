package com.juice.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.juice.RegisterComposeActivity
import com.juice.core.card.CardOrder
import com.juice.settings.JuiceSettings
import com.juice.ui.overlay.Hud
import com.juice.ui.overlay.OverlayActions
import com.juice.ui.overlay.TAG_CARD_ORDER_PREFIX
import com.juice.ui.overlay.TAG_FLAVOUR_PREFIX
import com.juice.ui.overlay.TAG_GROQ_KEY_FIELD
import com.juice.ui.overlay.TAG_LIQUID_QUALITY_PREFIX
import com.juice.ui.overlay.TAG_SETTINGS_DONE
import com.juice.ui.overlay.TAG_SWIPE_DISTANCE_PREFIX
import com.juice.core.liquid.LiquidQuality
import com.juice.core.progression.Flavour
import com.juice.settings.SwipeDistance
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val start = JuiceSettings(sound = true, onDeviceOnly = false)
    private val changes = mutableListOf<JuiceSettings>()
    private val actions = OverlayActions(onSettingsChange = { t -> changes += t(start) })

    private fun show() = compose.setContent { SettingsScreen(start, Hud(), actions) }

    @Test
    fun showsTheSheetsRowsAndNoDonePill() {
        show()
        listOf(
            TAG_SWIPE_DISTANCE_PREFIX + SwipeDistance.Short.name,
            TAG_CARD_ORDER_PREFIX + CardOrder.Newest.name,
            TAG_LIQUID_QUALITY_PREFIX + LiquidQuality.BALANCED.name,
            TAG_FLAVOUR_PREFIX + Flavour.ELECTRIC_CITRUS.name,
            TAG_GROQ_KEY_FIELD,
        ).forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag(TAG_SETTINGS_DONE).assertDoesNotExist()
    }

    @Test
    fun togglingASwitchSavesACopy() {
        show()
        compose.onNodeWithText("Sound").performClick()
        assertEquals(listOf(start.copy(sound = false)), changes)
    }

    @Test
    fun pickingAChoiceSavesACopy() {
        show()
        compose.onNodeWithTag(TAG_CARD_ORDER_PREFIX + CardOrder.Oldest.name).performScrollTo().performClick()
        assertTrue(changes.single().cardOrder == CardOrder.Oldest)
    }
}
