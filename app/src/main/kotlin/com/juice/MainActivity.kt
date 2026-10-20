package com.juice

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juice.core.Juice
import com.juice.onboarding.OnboardingScreen
import com.juice.settings.JuiceSettings
import com.juice.settings.SettingsRepository
import com.juice.settings.SettingsScreen
import kotlinx.coroutines.launch
import com.juice.overlay.OverlayPermission
import com.juice.overlay.OverlayService

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        // Guide to the overlay settings once on first run; afterwards the on-screen button does it.
        if (savedInstanceState == null && !OverlayPermission.isGranted(this)) {
            startActivity(OverlayPermission.settingsIntent(this))
        }
        setContent {
            MaterialTheme {
                val repository = remember { SettingsRepository(this@MainActivity) }
                val scope = rememberCoroutineScope()
                val onboardingSeen by repository.onboardingSeen.collectAsState(initial = null)
                val settings by repository.settings.collectAsState(initial = JuiceSettings())
                if (onboardingSeen == false) {
                    OnboardingScreen(onContinue = { scope.launch { repository.markOnboardingSeen() } })
                    return@MaterialTheme
                }
                Column(Modifier.padding(24.dp)) {
                    Text(Juice.NAME)
                    if (!overlayGranted) {
                        Text(stringResource(R.string.overlay_permission_title))
                        Text(stringResource(R.string.overlay_permission_body))
                        Button(onClick = { startActivity(OverlayPermission.settingsIntent(this@MainActivity)) }) {
                            Text(stringResource(R.string.overlay_permission_open_settings))
                        }
                    }
                    SettingsScreen(settings, onChange = { t -> scope.launch { repository.update(t) } })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = OverlayPermission.isGranted(this)
        if (overlayGranted) OverlayService.start(this)
    }
}
