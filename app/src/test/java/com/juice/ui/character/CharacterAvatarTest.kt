package com.juice.ui.character

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juice.RegisterComposeActivity
import com.juice.core.character.Characters
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
class CharacterAvatarTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val pete = Characters.Jim.copy(id = "pete", name = "Pete the Plumber")

    private fun show(animate: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent { CharacterAvatar(pete, 64.dp, animate, Modifier.testTag("avatar")) }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun showsInitials() {
        show(animate = false)
        compose.onNodeWithText("PP").assertTextEquals("PP")
    }

    @Test fun jimGetsAMonogramToo() {
        compose.setContent { CharacterAvatar(Characters.Jim, 64.dp, animate = false) }
        compose.onNodeWithText("CJ").assertTextEquals("CJ")
    }

    @Test fun animatedAvatarMovesOverTime() {
        show(animate = true)
        val before = compose.onNodeWithTag("avatar").getBoundsInRoot()
        compose.mainClock.advanceTimeBy(800)
        assertNotEquals(before, compose.onNodeWithTag("avatar").getBoundsInRoot())
    }

    @Test fun staticAvatarStaysPut() {
        show(animate = false)
        val before = compose.onNodeWithTag("avatar").getBoundsInRoot()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(before, compose.onNodeWithTag("avatar").getBoundsInRoot())
    }
}
