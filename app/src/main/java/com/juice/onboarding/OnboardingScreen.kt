package com.juice.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juice.R

/** First-run disclosure: input text goes to a cloud parser unless on-device only is on. */
@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onboarding_title))
        Text(stringResource(R.string.onboarding_cloud_disclosure))
        Button(onClick = onContinue) { Text(stringResource(R.string.onboarding_continue)) }
    }
}
