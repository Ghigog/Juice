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
import android.view.Gravity
import android.view.MotionEvent
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
import com.juice.feedback.FeedbackPlayer
import com.juice.feedback.FeedbackSettings
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
    private var overlayLifecycle: OverlayLifecycleOwner? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sounds by lazy { SoundPoolOutput(this) }
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
        if (strip == null && overlay == null) showStrip()
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

    private fun showStrip() {
        val view = View(this).apply {
            setBackgroundColor(STRIP_COLOR)
            setOnTouchListener(SwipeLeftListener(::swapToOverlay))
        }
        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            (metrics.widthPixels * STRIP_WIDTH_FRACTION).toInt(),
            (STRIP_HEIGHT_DP * metrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        windowManager.addView(view, params)
        strip = view
    }

    private fun swapToOverlay() {
        removeStrip()
        showOverlay()
    }

    private fun closeOverlay() {
        removeOverlay()
        showStrip()
    }

    private fun showOverlay() {
        val backdrop = Backdrop.choose(
            blurEnabled = BLUR_ENABLED,
            blurSupported = windowManager.isCrossWindowBlurEnabled,
            batterySaver = getSystemService(PowerManager::class.java).isPowerSaveMode,
        )
        val reservoir = ThirstReservoirView(this)
        val session = GameSession(
            EconomyStore(this),
            FeedbackPlayer(sounds, VibratorOutput(this)) { FeedbackSettings() },
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
        scope.launch {
            val state = OverlayShellState(
                repository,
                scope,
                repository.active(),
                onCompleted = { item -> session.complete(item.type.toEconomy()); render() },
                onCompletionUndone = { session.undo(); render() },
            )
            composeView.setContent { OverlayShell(state) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
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
        private const val STRIP_WIDTH_FRACTION = 0.3f
        private const val STRIP_HEIGHT_DP = 24
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        private val STRIP_COLOR = Color.argb(96, 255, 255, 255)
        private const val BLUR_ENABLED = true // TODO: read from the blur setting once settings exist
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

/** Fires [onSwipeLeft] once a touch on the strip travels left past the touch slop. */
internal class SwipeLeftListener(private val onSwipeLeft: () -> Unit) : View.OnTouchListener {
    private var downX = 0f
    private var fired = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                fired = false
            }
            MotionEvent.ACTION_MOVE -> if (!fired && downX - event.rawX > view.touchSlop()) {
                fired = true
                onSwipeLeft()
            }
        }
        return true
    }

    private fun View.touchSlop() = android.view.ViewConfiguration.get(context).scaledTouchSlop * 4
}
