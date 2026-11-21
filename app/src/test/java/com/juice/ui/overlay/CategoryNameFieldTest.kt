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
                    onSave = {},
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
}
