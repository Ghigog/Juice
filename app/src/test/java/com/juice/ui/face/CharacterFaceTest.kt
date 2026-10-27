package com.juice.ui.face

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.juice.RegisterComposeActivity
import com.juice.core.character.Characters
import com.juice.core.character.Expression
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CharacterFaceTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    @Test fun rendersEveryExpressionStill() {
        val expression = mutableStateOf(Expression.NEUTRAL)
        compose.setContent { CharacterFace(Characters.Jim.face, expression.value) }
        Expression.entries.forEach {
            expression.value = it
            compose.waitForIdle()
        }
    }

    @Test fun theAnimatedFaceFollowsEveryExpression() {
        val expression = mutableStateOf(Expression.NEUTRAL)
        lateinit var animator: com.juice.core.character.face.FaceAnimator
        compose.setContent {
            animator = rememberFaceAnimator(Characters.Nutritionist, expression.value)
            AnimatedCharacterFace(animator, FaceColors.of(Characters.Nutritionist), Modifier.size(120.dp), "Nora")
        }
        compose.onNodeWithContentDescription("Nora").assertIsDisplayed()
        Expression.entries.forEach {
            expression.value = it
            compose.waitForIdle()
            assertEquals(it, animator.expression)
        }
    }
}
