package com.juice.ui.liquid

import android.graphics.RuntimeShader
import android.os.Build
import androidx.test.filters.SdkSuppress
import org.junit.Test

/** Robolectric cannot compile AGSL, so this runs on a device. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class LiquidShaderTest {
    @Test
    fun agslCompiles() {
        // Throws IllegalArgumentException on invalid AGSL, e.g. a non-constant uniform array index.
        RuntimeShader(LiquidShaders.COMPOSITE)
    }
}
