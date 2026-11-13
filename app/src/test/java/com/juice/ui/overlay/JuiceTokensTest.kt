package com.juice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.juice.RegisterComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JuiceTokensTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    @Test fun touchTargetIsAtLeast44TallAndTheVisiblePillKeepsItsHeight() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.testTag("touch").touchTarget({ clicks++ }).width(80.dp)) {
                Box(Modifier.testTag("pill").width(80.dp).height(32.dp).background(Color.Red))
            }
        }
        val touch = compose.onNodeWithTag("touch").getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithTag("pill", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(touch.height >= 44.dp)
        assertEquals(32.dp, pill.height)
        compose.onNodeWithTag("touch").performClick()
        assertEquals(1, clicks)
    }

    @Test fun groundUsesTheGroundTokens() {
        assertEquals(Color(0xFF0F2E35), JuiceColors.GroundTeal)
        assertEquals(JuiceColors.Ink, JuiceColors.GroundInk)
    }
}
