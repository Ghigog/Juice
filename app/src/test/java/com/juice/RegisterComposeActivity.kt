package com.juice

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/**
 * createComposeRule launches ComponentActivity, which only the debug manifest declares
 * (ui-test-manifest). Registering it here lets the same tests run in the release variant.
 */
class RegisterComposeActivity : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager)
            .addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
    }
}
