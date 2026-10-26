package com.juice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ManifestPermissionsTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun declaresInternetPermissionForCoachRequests() {
        assertTrue(
            manifest.contains("""<uses-permission android:name="android.permission.INTERNET" />"""),
            "AndroidManifest.xml must declare android.permission.INTERNET",
        )
    }
}
