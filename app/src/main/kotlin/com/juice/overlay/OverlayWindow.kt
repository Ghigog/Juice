package com.juice.overlay

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher

private const val TAG = "OverlayService"

/**
 * The overlay window must be hardware accelerated: system windows are not by default, and the
 * Thirst Reservoir's RuntimeShader cannot draw on a software canvas.
 */
internal fun overlayLayoutParams() = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT,
).apply {
    // The window keeps its full size when the keyboard opens and the content moves up inside it
    // (the IME inset). Resizing the window instead left the screen behind bare while it shrank.
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    // Cover the status and navigation bars too, so the blur and tint run edge to edge;
    // the content keeps clear of them with inset padding.
    fitInsetsTypes = 0
    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
}

/** Builds the overlay; if that fails, cleans up, brings the handle back and reports why instead of crashing. */
internal fun guardedShowOverlay(
    show: () -> Unit,
    cleanup: () -> Unit,
    restoreStrip: () -> Unit,
    onFailure: (Exception) -> Unit = {},
) {
    try {
        show()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open the overlay; restoring the handle", e)
        cleanup()
        restoreStrip()
        onFailure(e)
    }
}

/**
 * The overlay window, as a dialog so it has a PhoneWindow and with it the public background blur
 * (Window.setBackgroundBlurRadius). That blur needs a translucent window theme. Back, whether a
 * key or a predictive back gesture, calls [onBack] instead of closing the dialog.
 */
@Suppress("DEPRECATION") // statusBarColor and navigationBarColor, still read before Android 15
internal fun overlayDialog(context: Context, content: View, onBack: () -> Unit): Dialog =
    Dialog(context, android.R.style.Theme_Translucent_NoTitleBar).apply {
        setCancelable(false)
        // On show, after Dialog.onStart registers its own default callback at the same priority;
        // the latest registration wins, so registering earlier would leave Back to the dialog.
        setOnShowListener {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { onBack() }
        }
        setContentView(content)
        window!!.apply {
            // After setContentView, so these replace what the theme set up.
            attributes = overlayLayoutParams()
            setDecorFitsSystemWindows(false)
            // Without this flag the window paints black behind the status and navigation bars.
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            isStatusBarContrastEnforced = false
            isNavigationBarContrastEnforced = false
        }
    }

/**
 * The window background blur for [backdrop] at [radius]. Under blur it never drops to 0 while the
 * window is up, since a 0 radius tears down the blur drawable and the next frame rebuilds it.
 */
internal fun windowBlurRadius(backdrop: Backdrop, radius: Int): Int =
    if (backdrop == Backdrop.BLUR) radius.coerceAtLeast(1) else 0
