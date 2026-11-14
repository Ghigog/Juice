package com.juice.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juice.R
import com.juice.ui.overlay.ENTRANCE_TOTAL_MILLIS
import com.juice.ui.overlay.JuiceColors
import com.juice.ui.overlay.JuiceShapes
import com.juice.ui.overlay.JuiceType
import com.juice.ui.overlay.SpaceGrotesk
import com.juice.ui.overlay.entrance
import com.juice.ui.overlay.overlayGround
import com.juice.ui.overlay.touchTarget

const val TAG_ONBOARDING_TITLE = "onboarding_title"
const val TAG_ONBOARDING_DISCLOSURE = "onboarding_disclosure"
const val TAG_ONBOARDING_CONTINUE = "onboarding_continue"

/**
 * First-run disclosure: input text goes to a cloud parser unless on-device only is on. It sits on the overlay's
 * ground in a glass card; the title, disclosure and [accent] pill rise in one after another unless [reduceMotion].
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit, accent: Color, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, tween(ENTRANCE_TOTAL_MILLIS, easing = LinearEasing))
    }
    Box(
        modifier.fillMaxSize().background(overlayGround(1f)).systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(JuiceColors.white(0.14f), JuiceShapes.Card)
                .border(1.dp, JuiceColors.white(0.28f), JuiceShapes.Card)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                stringResource(R.string.onboarding_title),
                Modifier.testTag(TAG_ONBOARDING_TITLE).entrance({ progress.value }, 0),
                style = TextStyle(
                    fontFamily = SpaceGrotesk, fontSize = JuiceType.Title, lineHeight = JuiceType.TitleLine,
                    fontWeight = FontWeight.Medium, color = Color.White,
                ),
            )
            BasicText(
                stringResource(R.string.onboarding_cloud_disclosure),
                Modifier.testTag(TAG_ONBOARDING_DISCLOSURE).entrance({ progress.value }, 1),
                style = TextStyle(
                    fontFamily = SpaceGrotesk, fontSize = JuiceType.Body, lineHeight = JuiceType.BodyLine,
                    color = JuiceColors.white(0.85f),
                ),
            )
            Box(Modifier.testTag(TAG_ONBOARDING_CONTINUE).entrance({ progress.value }, 2).touchTarget(onContinue)) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).background(accent, JuiceShapes.Pill),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        stringResource(R.string.onboarding_continue),
                        style = TextStyle(
                            fontFamily = SpaceGrotesk, fontSize = JuiceType.Button, fontWeight = FontWeight.Bold,
                            color = JuiceColors.Ink, textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}
