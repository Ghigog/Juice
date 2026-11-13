package com.juice.overlay

import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import java.util.function.Consumer

/**
 * The overlay window's backdrop: blur when it is on, supported and battery saver is off, otherwise
 * the scrim. The blur is the window's own background blur, drawn by its render thread with each
 * frame. Unlike FLAG_BLUR_BEHIND it needs no window relayout, and no shared system dim layer
 * that the window manager drops whenever it judges the window not visible for a pass.
 */
internal class OverlayBlur(
    private val windowManager: WindowManager,
    private val powerManager: PowerManager,
    private val blurEnabled: () -> Boolean,
    /** The overlay's window, or null while there is none. */
    private val window: () -> Window?,
) {
    val backdrop = mutableStateOf(Backdrop.SCRIM)

    // The blur radius the window should have, which ramps up with the reveal.
    private var radius = 0

    /** The system turns cross-window blur off and on (power saving, heat, some transitions); follow it. */
    val listener = Consumer<Boolean> { refresh() }

    /** A window just came up: choose its backdrop and start the blur from nothing. */
    fun reset() {
        backdrop.value = choose()
        radius = 0
        apply()
    }

    /** Sets the window blur to follow the reveal [progress]; an unchanged radius is not re-applied. */
    fun ramp(progress: Float) {
        if (window() == null) return
        val next = blurRadiusFor(progress)
        if (next == radius) return
        // Tracked even under the scrim, so a later switch to blur starts from the right radius.
        radius = next
        apply()
    }

    /** Re-chooses the backdrop; call when the blur setting or the system's blur support changed. */
    fun refresh() {
        if (window() == null) return
        val next = choose()
        if (next == backdrop.value) return
        backdrop.value = next
        apply()
    }

    private fun apply() {
        window()?.setBackgroundBlurRadius(windowBlurRadius(backdrop.value, radius))
    }

    private fun choose() = Backdrop.choose(
        blurEnabled = blurEnabled(),
        blurSupported = windowManager.isCrossWindowBlurEnabled,
        batterySaver = powerManager.isPowerSaveMode,
    )
}
