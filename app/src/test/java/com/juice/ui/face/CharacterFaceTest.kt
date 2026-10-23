package com.juice.ui.face

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.juice.RegisterComposeActivity
import com.juice.core.character.Characters
import com.juice.core.character.Expression
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class CharacterFaceTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    @Test fun rendersEveryExpression() {
        val expression = mutableStateOf(Expression.NEUTRAL)
        compose.setContent { CharacterFace(Characters.Jim.face, expression.value) }
        Expression.entries.forEach {
            expression.value = it
            compose.waitForIdle()
        }
    }
}
