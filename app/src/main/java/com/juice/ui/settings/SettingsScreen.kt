package com.juice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juice.R
import com.juice.settings.CharacterSettings
import com.juice.settings.JuiceSettings
import com.juice.ui.overlay.Hud
import com.juice.ui.overlay.JuiceColors
import com.juice.ui.overlay.OverlayActions
import com.juice.ui.overlay.SettingsContent
import com.juice.ui.overlay.SpaceGrotesk

/** The overlay's settings sheet, full screen in the app: same rows and look, nothing to close. */
@Composable
fun SettingsScreen(
    settings: JuiceSettings,
    hud: Hud,
    actions: OverlayActions,
    characters: CharacterSettings? = null,
    groqKeySaved: Boolean = false,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = SpaceGrotesk, color = Color.White)) {
        Column(
            modifier
                .fillMaxSize()
                .background(JuiceColors.Sheet)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            header()
            Text(
                stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
            SettingsContent(settings, hud, actions, groqKeySaved, characters = characters)
        }
    }
}
