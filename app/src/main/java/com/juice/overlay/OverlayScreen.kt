package com.juice.overlay

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp

/**
 * The screen the handle and the overlay are laid out for: its bounds, and the room kept clear for
 * the status bar, navigation bar and cutout on every side. Read from the display (when the service
 * starts and when the screen turns) rather than from inset callbacks, which reach an overlay window
 * late or not at all and made the layout jump. In landscape the navigation bar and the cutout sit at the sides.
 */
internal class OverlayScreen(private val context: Context, private val windowManager: WindowManager) {
    /** Room the overlay keeps clear for the system bars and cutout; follows the screen as it rotates. */
    val systemBars = mutableStateOf(PaddingValues(0.dp))

    // The screen's size and insets last laid out for.
    private var laidOut: Pair<Rect, Insets> = current()

    init {
        systemBars.value = systemBarsPadding()
    }

    val bounds: Rect get() = windowManager.maximumWindowMetrics.bounds

    private val density get() = context.resources.displayMetrics.density

    private fun current(): Pair<Rect, Insets> {
        val metrics = windowManager.maximumWindowMetrics
        return metrics.bounds to metrics.windowInsets.getInsetsIgnoringVisibility(SAFE_INSETS)
    }

    /** True, with [systemBars] brought up to date, when the screen's size or insets are not what was last laid out for. */
    fun changed(): Boolean {
        val now = current()
        if (now == laidOut) return false
        laidOut = now
        systemBars.value = systemBarsPadding()
        return true
    }

    /** How far in the handle's centre must sit: its window on screen, its logo clear of the bars and cutout. */
    fun handleEdges(): HandleEdges {
        val insets = current().second
        return HandleEdges.of(
            SliceHandleView.EDGE_DP * density, SliceHandleView.LOGO_HALF_DP * density,
            insets.left, insets.top, insets.right, insets.bottom,
        )
    }

    /** Where the overlay's backdrop slice sits on this screen. */
    fun backdropSliceSpot(): SliceSpot = backdropSliceSpot(bounds.width(), bounds.height(), density)

    private fun systemBarsPadding(): PaddingValues {
        val insets = current().second
        return PaddingValues.Absolute(
            left = (insets.left / density).dp,
            top = (insets.top / density).dp,
            right = (insets.right / density).dp,
            bottom = (insets.bottom / density).dp,
        )
    }

    private companion object {
        val SAFE_INSETS = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
    }
}
