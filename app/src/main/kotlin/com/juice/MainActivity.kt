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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juice.core.Juice
import com.juice.overlay.OverlayPermission
import com.juice.overlay.OverlayService

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent {
            MaterialTheme {
                Column(Modifier.padding(24.dp)) {
                    Text(Juice.NAME)
                    if (!overlayGranted) {
                        Text(stringResource(R.string.overlay_permission_title))
                        Text(stringResource(R.string.overlay_permission_body))
                        Button(onClick = { startActivity(OverlayPermission.settingsIntent(this@MainActivity)) }) {
                            Text(stringResource(R.string.overlay_permission_open_settings))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = OverlayPermission.isGranted(this)
        if (overlayGranted) OverlayService.start(this)
        else startActivity(OverlayPermission.settingsIntent(this))
    }
}
