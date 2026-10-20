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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import com.juice.R
import com.juice.ui.reservoir.ThirstReservoirView

/** Foreground service owning the trigger strip window and the full-screen overlay window. */
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var strip: View? = null
    private var overlay: View? = null

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
        val root = FrameLayout(this).apply {
            setBackgroundColor(SCRIM_COLOR)
            addView(
                ThirstReservoirView(context).apply { fill = PLACEHOLDER_FILL },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                Button(context).apply {
                    setText(R.string.overlay_close)
                    setOnClickListener { closeOverlay() }
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
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
    }

    companion object {
        private const val CHANNEL_ID = "juice_overlay"
        private const val NOTIFICATION_ID = 1
        private const val STRIP_WIDTH_FRACTION = 0.3f
        private const val STRIP_HEIGHT_DP = 24
        private const val PLACEHOLDER_FILL = 0.5f // until the Thirst Meter is persisted
        private val STRIP_COLOR = Color.argb(96, 255, 255, 255)
        private val SCRIM_COLOR = Color.argb(200, 0, 0, 0)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
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
