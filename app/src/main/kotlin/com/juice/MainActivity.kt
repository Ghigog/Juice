package com.juice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.juice.core.progression.Progression
import com.juice.llm.GroqClient
import com.juice.llm.KeyCheck
import com.juice.ui.overlay.Hud
import com.juice.ui.overlay.JuiceColors
import com.juice.ui.overlay.JuiceShapes
import com.juice.ui.overlay.JuiceType
import com.juice.ui.overlay.OverlayActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juice.onboarding.OnboardingScreen
import com.juice.overlay.OverlayPermission
import com.juice.overlay.OverlayService
import com.juice.settings.JuiceSettings
import com.juice.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        // Guide to the overlay settings once on first run; afterwards the on-screen button does it.
        if (savedInstanceState == null && !OverlayPermission.isGranted(this)) {
            startActivity(OverlayPermission.settingsIntent(this))
        }
        offerBatteryExemptionOnce()
        setContent {
            MaterialTheme {
                val container = (applicationContext as JuiceApplication).container
                val repository = container.settings
                val scope = rememberCoroutineScope()
                val characterSettings = remember { container.characterSettings(scope) }
                val onboardingSeen by repository.onboardingSeen.collectAsState(initial = null)
                val settings by repository.settings.collectAsState(initial = JuiceSettings())
                if (onboardingSeen == false) {
                    OnboardingScreen(onContinue = { scope.launch { repository.markOnboardingSeen() } })
                    return@MaterialTheme
                }
                val groqKeyStore = container.groqKeyStore
                val groqKeyMasked = remember { MutableStateFlow<String?>(null) }
                val groqKeySaved by groqKeyStore.hasKey.collectAsState()
                var level by remember { mutableIntStateOf(1) }
                LaunchedEffect(groqKeySaved) {
                    groqKeyMasked.value = if (groqKeySaved) withContext(Dispatchers.IO) { groqKeyStore.load()?.masked } else null
                }
                LaunchedEffect(settings) {
                    level = withContext(Dispatchers.IO) { Progression.levelFor(container.economyStore.load()?.state?.juice ?: 0L) }
                }
                val hud = Hud(level = level, flavour = settings.flavour?.takeIf { it.unlockLevel <= level } ?: Progression.flavourFor(level))
                val actions = OverlayActions(
                    onSettingsChange = { t -> scope.launch { repository.update(t) } },
                    groqKeyMasked = groqKeyMasked,
                    onSaveGroqKey = { key, onChecked ->
                        scope.launch {
                            val check = withContext(Dispatchers.IO) {
                                groqKeyStore.save(key)
                                runCatching { GroqClient(key.value).checkKey() }.getOrDefault(KeyCheck.Unreachable)
                            }
                            groqKeyMasked.value = key.masked
                            onChecked(check)
                        }
                    },
                    onRemoveGroqKey = {
                        groqKeyStore.remove()
                        groqKeyMasked.value = null
                    },
                )
                SettingsScreen(settings, hud, actions, characters = characterSettings, groqKeySaved = groqKeySaved) {
                    if (!overlayGranted) PermissionPrompt(hud) {
                        startActivity(OverlayPermission.settingsIntent(this@MainActivity))
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionPrompt(hud: Hud, onOpen: () -> Unit) {
        Column(Modifier.padding(bottom = 20.dp)) {
            Text(stringResource(R.string.overlay_permission_title), fontSize = JuiceType.Chat, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.overlay_permission_body), fontSize = JuiceType.Caption, color = JuiceColors.white(0.7f))
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .heightIn(min = 44.dp)
                    .clip(JuiceShapes.Pill)
                    .background(JuiceColors.white(0.14f))
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.overlay_permission_open_settings), fontSize = JuiceType.Label, fontWeight = FontWeight.Bold) }
        }
    }

    /** Asks once for the battery-optimisation exemption so the strip is not killed in the background. */
    @SuppressLint("BatteryLife")
    private fun offerBatteryExemptionOnce() {
        val prefs = getSharedPreferences("juice_prompts", Context.MODE_PRIVATE)
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (exempt || prefs.getBoolean("battery_exemption_asked", false)) return
        prefs.edit().putBoolean("battery_exemption_asked", true).apply()
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        )
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = OverlayPermission.isGranted(this)
        if (overlayGranted) OverlayService.start(this)
    }
}
