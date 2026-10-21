package com.juice.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.juice.core.economy.ItemType as EconomyItemType
import com.juice.core.intake.ItemType
import com.juice.data.EconomyStore
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.intake.CaptureIntake
import com.juice.intake.IntakeWorker
import com.juice.settings.JuiceSettings
import com.juice.settings.SettingsRepository
import com.juice.feedback.FeedbackPlayer
import com.juice.feedback.SoundPoolOutput
import com.juice.feedback.VibratorOutput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.juice.ui.GameSession
import com.juice.ui.overlay.OverlayShell
import com.juice.ui.overlay.OverlayShellState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.juice.R
import com.juice.ui.reservoir.ThirstReservoirView

/** Foreground service owning the trigger strip window and the full-screen overlay window. */
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var strip: View? = null
    private var overlay: View? = null
    private var starting = false
    private var overlayLifecycle: OverlayLifecycleOwner? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sounds by lazy { SoundPoolOutput(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    @Volatile private var settings = JuiceSettings()
    private val repository by lazy { ItemRepository(JuiceDatabase.open(this).itemDao()) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!OverlayPermission.isGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (strip == null && overlay == null && !starting) {
            starting = true
            scope.launch {
                settings = settingsRepository.current()
                if (strip == null && overlay == null) showStrip()
                settingsRepository.settings.collect { applySettings(it) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeStrip()
        removeOverlay()
        scope.cancel()
        sounds.release()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .build()
    }

    /** Keeps [settings] current and re-lays out the strip when its position or width changed. */
    private fun applySettings(next: JuiceSettings) {
        val layoutChanged = next.stripPosition != settings.stripPosition ||
            next.stripWidthPercent != settings.stripWidthPercent
        settings = next
        val view = strip ?: return
        if (layoutChanged) windowManager.updateViewLayout(view, stripParams())
    }

    private fun stripParams(): WindowManager.LayoutParams {
        val metrics = resources.displayMetrics
        return WindowManager.LayoutParams(
            settings.stripWidthPx(metrics.widthPixels),
            (STRIP_HEIGHT_DP * metrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = settings.stripPosition.gravity() }
    }

    private fun showStrip() {
        val view = View(this).apply {
            setBackgroundColor(STRIP_COLOR)
            setOnTouchListener(SwipeLeftListener(::swapToOverlay))
        }
        val params = stripParams()
        windowManager.addView(view, params)
        strip = view
    }

    private fun swapToOverlay() {
        removeStrip()
        guardedShowOverlay(::showOverlay, ::discardOverlay, ::showStrip)
    }

    private fun discardOverlay() = runCatching { removeOverlay() }.onFailure { overlay = null }

    private fun closeOverlay() {
        removeOverlay()
        showStrip()
    }

    private fun showOverlay() {
        val backdrop = Backdrop.choose(
            blurEnabled = settings.blur,
            blurSupported = windowManager.isCrossWindowBlurEnabled,
            batterySaver = getSystemService(PowerManager::class.java).isPowerSaveMode,
        )
        val reservoir = ThirstReservoirView(this).apply {
            sloshEnabled = settings.sloshEnabled
            particlesEnabled = settings.particlesEnabled
        }
        val session = GameSession(
            EconomyStore(this),
            FeedbackPlayer(sounds, VibratorOutput(this)) { settings.feedback },
            onSatiated = reservoir::emitParticles,
        )
        session.open()
        val juiceText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 24f }
        val badgeText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f }
        fun render() {
            reservoir.fill = session.fill
            juiceText.text = getString(R.string.overlay_juice, session.state.juice)
            badgeText.text = getString(R.string.overlay_multiplier, session.multiplier)
        }
        render()
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(this)
        val root = FrameLayout(this).apply {
            setBackgroundColor(if (backdrop == Backdrop.BLUR) BLUR_TINT_COLOR else SCRIM_COLOR)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(reservoir, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(composeView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(juiceText)
                    addView(badgeText)
                    addView(
                        Button(context).apply {
                            setText(R.string.overlay_close)
                            setOnClickListener { closeOverlay() }
                        },
                    )
                },
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END),
            )
        }
        overlayLifecycle = lifecycleOwner
        val intake = CaptureIntake(
            repository,
            onDeviceOnly = { settingsRepository.current().onDeviceOnly },
            enqueue = { id, text, onDevice -> IntakeWorker.enqueue(this, id, text, onDevice) },
        )
        scope.launch {
            val state = OverlayShellState(
                repository,
                scope,
                repository.active(),
                onCompleted = { item -> session.complete(item.type.toEconomy()); render() },
                onCompletionUndone = { session.undo(); render() },
                onCaptured = intake::submit,
            )
            state.observe()
            composeView.setContent { OverlayShell(state, tapButtons = settings.tapButtons) }
        }
        val params = overlayLayoutParams()
        if (backdrop == Backdrop.BLUR) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = BLUR_RADIUS_PX
        }
        windowManager.addView(root, params)
        overlay = root
    }

    private fun removeStrip() {
        strip?.let(windowManager::removeView)
        strip = null
    }

    private fun removeOverlay() {
        overlay?.let(windowManager::removeView)
        overlay = null
        overlayLifecycle?.stop()
        overlayLifecycle = null
    }

    companion object {
        private const val CHANNEL_ID = "juice_overlay"
        private const val NOTIFICATION_ID = 1
        private const val STRIP_HEIGHT_DP = 24
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        private val STRIP_COLOR = Color.argb(96, 255, 255, 255)
        private const val BLUR_RADIUS_PX = 40
        private val BLUR_TINT_COLOR = Color.argb(60, 0, 0, 0)
        private val SCRIM_COLOR = Color.argb(200, 0, 0, 0)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}

private fun ItemType.toEconomy() = when (this) {
    ItemType.Note -> EconomyItemType.NOTE
    ItemType.Task -> EconomyItemType.TASK
    ItemType.Goal -> EconomyItemType.GOAL
    ItemType.Project -> EconomyItemType.PROJECT
}

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
)

/** Builds the overlay; if that fails, cleans up and brings the strip back instead of crashing. */
internal fun guardedShowOverlay(show: () -> Unit, cleanup: () -> Unit, restoreStrip: () -> Unit) {
    try {
        show()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open the overlay; restoring the strip", e)
        cleanup()
        restoreStrip()
    }
}
