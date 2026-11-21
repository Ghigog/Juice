package com.juice.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.juice.RegisterComposeActivity
import com.juice.core.category.Category
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import com.juice.core.category.CategoryPalette
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class CategoryNameFieldTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    @Test
    fun inputAndButtonsSitFullyAboveTheKeyboardGap() {
        val keyboard = 300.dp
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().padding(bottom = keyboard)) {
                CategoryNameField(
                    naming = CategoryNaming(renaming = Category("c1", "Work")),
                    hud = Hud(),
                    reduceMotion = true,
                    onSave = { _, _ -> },
                    onCancel = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val field = compose.onNodeWithTag(TAG_CATEGORY_NAME_FIELD).getUnclippedBoundsInRoot()
        for (tag in listOf(TAG_CATEGORY_NAME_INPUT, TAG_CATEGORY_NAME_SAVE, TAG_CATEGORY_NAME_CANCEL)) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= field.top, "$tag starts inside the field")
            assertTrue(bounds.bottom <= field.bottom, "$tag ends inside the field")
            assertTrue(bounds.bottom <= 800.dp - keyboard, "$tag sits above the keyboard gap")
        }
    }

    @Test
    fun tappingASwatchSelectsItPlaysFeedbackAndSavesItsAccent() {
        val purple = CategoryPalette.colours[2]
        var saved: Pair<String, Int?>? = null
        var taps = 0
        compose.setContent {
            CategoryNameField(
                naming = CategoryNaming(Category("c1", "Work")),
                hud = Hud(),
                reduceMotion = true,
                onSave = { name, colour -> saved = name to colour },
                onCancel = {},
                onPick = { taps++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val swatch = compose.onNodeWithTag(TAG_CATEGORY_SWATCH_PREFIX + purple.accent)
        assertTrue(swatch.getUnclippedBoundsInRoot().let { it.right - it.left >= 44.dp && it.bottom - it.top >= 44.dp })
        swatch.performClick()
        swatch.assertIsSelected()
        compose.onNodeWithTag(TAG_CATEGORY_NAME_SAVE).performClick()
        assertEquals(1, taps)
        assertEquals("Work" to purple.accent, saved)
    }

    @Test
    fun mainsNameIsReadOnlyButItsSwatchesWork() {
        compose.setContent {
            CategoryNameField(
                naming = CategoryNaming(Category(Category.MAIN_ID, Category.MAIN_NAME)),
                hud = Hud(), reduceMotion = true, onSave = { _, _ -> }, onCancel = {}, modifier = Modifier.fillMaxSize(),
            )
        }
        compose.onNodeWithTag(TAG_CATEGORY_NAME_INPUT).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        compose.onNodeWithTag(TAG_CATEGORY_SWATCH_PREFIX + CategoryPalette.colours[0].accent).assertIsDisplayed()
    }

    @Test
    fun hudAccentFollowsTheCategoryColourElseTheFlavour() {
        val berry = CategoryPalette.colours[1]
        assertEquals(androidx.compose.ui.graphics.Color(berry.accent), Hud(colour = berry).accent)
        assertEquals(androidx.compose.ui.graphics.Color(berry.deep), Hud(colour = berry).deep)
        assertEquals(androidx.compose.ui.graphics.Color(Hud().theme.accent), Hud().accent)
    }
}
