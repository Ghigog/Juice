package com.juice.overlay

import android.app.Dialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.juice.JuiceApplication
import com.juice.R
import com.juice.core.card.CardOrder
import com.juice.core.economy.EconomyConfig
import com.juice.core.progression.Progression
import com.juice.feedback.FeedbackEvent
import com.juice.intake.CaptureIntake
import com.juice.intake.IntakeWorker
import com.juice.settings.JuiceSettings
import com.juice.ui.GameSession
import com.juice.ui.Tier
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import com.juice.ui.overlay.OverlayActions
import com.juice.ui.overlay.OverlayEffects
import com.juice.ui.overlay.OverlayShell
import com.juice.ui.overlay.OverlayShellState
import com.juice.ui.overlay.WritingBack
import com.juice.ui.overlay.hud
import com.juice.ui.overlay.multiplierLabel
import com.juice.ui.overlay.toEconomy
import com.juice.ui.theme.FlavourTheme
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Foreground service owning the slice handle window and the full-screen overlay window. */
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var handle: SliceHandleView? = null
    private var overlay: View? = null
    private var overlayDialog: Dialog? = null
    private var starting = false
    private var handBack: HandBack? = null

    // Whether the overlay's own backdrop slice is drawn; the stage's flying slice covers it while it is not.
    private val overlaySliceShown = mutableStateOf(true)
    private var overlayLifecycle: OverlayLifecycleOwner? = null
    private val blur by lazy { OverlayBlur(windowManager, getSystemService(PowerManager::class.java), { settings.blur }) { overlayDialog?.window } }
    private val closeRequest = CloseRequest(reduceMotion = { settings.reduceMotion }, finish = ::closeOverlay)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val feedbackOutputs by lazy { OverlayFeedback(this) { settings.feedback } }
    private val container get() = (applicationContext as JuiceApplication).container
    private val settingsRepository get() = container.settings
    private val groqKeyStore get() = container.groqKeyStore
    private val groqKey by lazy { GroqKeyActions(groqKeyStore, container.groqFor, scope) }
    private val coachChats by lazy {
        CoachChats(
            registry = { characters.registry() }, disabledIds = characterStore::disabledIds, characters = { characters },
            conversationFor = container::coachConversationFor, coachTasks = container::coachTasks, scope = scope,
        )
    }
    @Volatile private var settings = JuiceSettings()
    private val characterStore get() = container.characterStore
    private val pendingCards get() = container.pendingCards
    private val repository get() = container.items
    private val characters get() = container.characters
    private val characterSettings by lazy { container.characterSettings(scope) }
    /** Service has no constructor arguments; tests set this before onCreate. */
    internal var clock: Clock = Clock.systemDefaultZone()
    private var openItems = 0
    internal val openItemCount get() = openItems
    internal val handleSlices get() = handle?.slices
    private lateinit var screen: OverlayScreen
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onScreenChanged()
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        screen = OverlayScreen(this, windowManager)
        // A turn of 180 degrees changes no configuration, only which side the bars and cutout are on.
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(mainLooper))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenChanged()
    }

    /**
     * The screen rotated or changed size: the handle goes back to its saved place on the new
     * screen, kept clear of the bars and cutout, and the overlay keeps clear of them where they now are.
     */
    private fun onScreenChanged() {
        if (!screen.changed()) return
        handle?.let { view -> handleCentre().let { (x, y) -> view.moveTo(x, y) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            OverlayNotification.ID,
            OverlayNotification.build(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!OverlayPermission.isGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val open = intent?.action == ACTION_OPEN
        if (handle == null && overlay == null && !starting) {
            starting = true
            scope.launch {
                settings = settingsRepository.current()
                if (handle == null && overlay == null) showHandle()
                if (open) swapToOverlay()
                settingsRepository.settings.collect { applySettings(it) }
            }
            scope.launch {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                val active = todayChanges(this@OverlayService, clock).flatMapLatest { day ->
                    repository.repairCadences(day)
                    repository.observeActive(day)
                }
                active.collect {
                    openItems = it.size
                    handle?.slices = SliceLogoPainter.sliceCount(openItems)
                }
            }
        } else if (open && handle != null && overlay == null) {
            swapToOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        removeHandle()
        removeOverlay()
        scope.cancel()
        feedbackOutputs.release()
        super.onDestroy()
    }

    /** Keeps [settings] current and moves or recolours the handle when its place or flavour changed. */
    private fun applySettings(next: JuiceSettings) {
        val moved = next.handleX != settings.handleX || next.handleY != settings.handleY
        val blurChanged = next.blur != settings.blur
        settings = next
        if (blurChanged) blur.refresh()
        val view = handle ?: return
        styleHandle(view)
        if (moved) handleCentre().let { (x, y) -> view.moveTo(x, y) }
    }

    private fun handleCentre(): Pair<Float, Float> {
        val bounds = screen.bounds
        return settings.handleCentre(bounds.width(), bounds.height(), screen.handleEdges())
    }

    /** The flavour the handle wears: the player's pick once unlocked, else the highest unlocked. */
    private fun styleHandle(view: SliceHandleView) {
        val juice = container.economyStore.load()?.state?.juice ?: 0L
        val level = Progression.levelFor(juice)
        val flavour = settings.flavour?.takeIf { it.unlockLevel <= level } ?: Progression.flavourFor(level)
        view.style = SliceStyle.of(flavour)
        view.accent = FlavourTheme.of(flavour).accent
    }

    /** Shows the handle; with [flyFrom] the slice flies in from there first and [onLanded] is called once the handle takes touches; [onStageDrawn] is called once the stage has drawn the slice. */
    private fun showHandle(flyFrom: SliceSpot? = null, onStageDrawn: () -> Unit = {}, onLanded: () -> Unit = {}) {
        val density = resources.displayMetrics.density
        val view = SliceHandleView(
            this,
            windowManager,
            radius = { settings.swipeDistance.dp * density },
            reduceMotion = { settings.reduceMotion },
            feedback = { feedbackOutputs.handle.play(it) },
            onMoved = { x, y ->
                val bounds = screen.bounds
                val edges = screen.handleEdges()
                val (fx, fy) = handleFractions(x, y, bounds.width(), bounds.height(), edges)
                settings = settings.copy(handleX = fx, handleY = fy)
                scope.launch { settingsRepository.update { it.copy(handleX = fx, handleY = fy) } }
                handleLanding(x, y, bounds.width(), bounds.height(), edges)
            },
            onDismiss = {
                removeHandle()
                stopSelf()
            },
            onOpen = { angle, spin -> swapToOverlay(angle, spin) },
        )
        styleHandle(view)
        view.slices = SliceLogoPainter.sliceCount(openItems)
        val (x, y) = handleCentre()
        handle = view
        if (flyFrom == null) view.show(x, y) else view.flyHome(x, y, flyFrom, animate = !settings.reduceMotion, onStageDrawn, onLanded)
    }

    /**
     * Opens the overlay from a swipe: the handle's window goes, the stage keeps drawing the slice
     * as it flies to the backdrop, and the stage is dismissed after the overlay's first frame.
     */
    private fun swapToOverlay(angle: Float = 0f, spinDegPerSec: Float = 0f) {
        openingAngle = angle
        openingSpin = spinDegPerSec
        val flying = handle
        if (flying == null) {
            guardedShowOverlay(::showOverlay, ::discardOverlay, ::showHandle, ::reportOverlayFailure)
            return
        }
        val target = screen.backdropSliceSpot()
        guardedShowOverlay(
            show = {
                handOverToOverlay(
                    fly = { onArrived -> flying.flyTo(target, animate = !settings.reduceMotion, onArrived) },
                    show = {
                        // The slice kept spinning in flight: carry on from where it is now.
                        openingAngle = flying.spinAngle
                        openingSpin = flying.spinSpeed
                        showOverlay(); overlaySliceShown.value = false
                    },
                    showOverlaySlice = { overlaySliceShown.value = true },
                    whenOverlayDrawn = { action -> overlay?.whenDrawn(action) ?: action() },
                    dismissStage = { flying.dismissStage(); if (handle === flying) handle = null },
                )
            },
            cleanup = { discardOverlay(); removeHandle() },
            restoreStrip = ::showHandle,
            onFailure = ::reportOverlayFailure,
        )
    }

    /** Puts the failure on screen as a toast. Nothing goes to the clipboard, which other apps and the keyboard can read. */
    internal fun reportOverlayFailure(e: Exception) {
        Toast.makeText(this, getString(R.string.overlay_failed, e.javaClass.simpleName, e.message), Toast.LENGTH_LONG).show()
    }

    private fun discardOverlay() = runCatching { removeOverlay() }.onFailure { overlay = null }

    /** The overlay's close, once the ground has drained (or at once with reduced motion): lands the slice, then swaps. */
    private fun closeOverlay() {
        if (overlay == null) return
        handBack?.drained() ?: run { removeOverlay(); showHandle() }
    }

    /** The ground starts to drain: the slice flies from the backdrop to the handle meanwhile. */
    private fun startHandBack() {
        if (overlay == null || handBack != null) return
        val from = screen.backdropSliceSpot()
        val back = HandBack(
            fly = { onStageDrawn, onLanded -> showHandle(from, onStageDrawn, onLanded) },
            hideOverlaySlice = { overlaySliceShown.value = false },
            finish = { handBack = null; removeOverlay() },
        )
        handBack = back
        back.draining()
    }

    /** The backdrop slice's opening spin, set by the pull that opens the overlay. */
    private var openingSpin = 0f
    private var openingAngle = 0f

    private fun showOverlay() {
        overlaySliceShown.value = true
        val spinOnOpen = openingSpin
        val angleOnOpen = openingAngle
        openingSpin = 0f
        openingAngle = 0f
        val feedback = feedbackOutputs.forOverlay()
        val scene = OverlayScene(
            context = this, container = container, scope = scope, settings = { settings }, feedback = feedback,
            groqKey = groqKey, coachChats = coachChats, closeRequest = closeRequest, blur = blur, screen = screen,
            characterSettings = characterSettings,
        )
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(this)
        val root = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(composeView, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        overlayLifecycle = lifecycleOwner
        scene.setContent(composeView, overlaySliceShown, spinOnOpen, angleOnOpen, onDraining = ::startHandBack)
        val dialog = overlayDialog(this, root) { scene.writingBack.back(closeRequest::request) }
        // Compose looks for its owners from the window's root view, which is the dialog's decor.
        dialog.window!!.decorView.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }
        overlayDialog = dialog
        blur.reset()
        dialog.show()
        overlay = root
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blur.listener)
    }

    private fun removeHandle() {
        handle?.dismiss()
        handle = null
    }

    private fun removeOverlay() {
        if (overlay != null) windowManager.removeCrossWindowBlurEnabledListener(blur.listener)
        overlayDialog?.dismiss()
        overlayDialog = null
        overlay = null
        handBack = null
        closeRequest.reset()
        overlayLifecycle?.stop()
        overlayLifecycle = null
    }

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT

        /** Starts the service and opens the overlay, for a notification's PendingIntent.getForegroundService. */
        const val ACTION_OPEN = "com.juice.overlay.action.OPEN"

        fun openIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_OPEN)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
