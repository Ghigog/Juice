package com.juice.overlay

import java.time.LocalDate
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.juice.BuildConfig
import com.juice.core.intake.ItemType
import com.juice.coach.CoachClient
import com.juice.coach.CoachConversation
import com.juice.coach.ConversationStore
import com.juice.core.economy.EconomyConfig
import com.juice.data.PendingCoachCardStore
import com.juice.memory.MemoryStore
import com.juice.ui.overlay.CoachChatState
import com.juice.data.CreatedCharacterStore
import com.juice.intake.CharacterGenerator
import com.juice.intake.GroqClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import com.juice.feedback.FeedbackEvent
import com.juice.ui.Tier
import com.juice.ui.overlay.LiquidTouchPhase
import com.juice.ui.overlay.OverlayActions
import com.juice.ui.overlay.OverlayEffects
import com.juice.ui.overlay.hud
import com.juice.ui.overlay.multiplierLabel
import com.juice.ui.overlay.toEconomy
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
import com.juice.data.EconomyStore
import com.juice.data.ItemRepository
import com.juice.notify.ReminderScheduler
import com.juice.data.JuiceDatabase
import com.juice.intake.CaptureIntake
import com.juice.intake.IntakeWorker
import com.juice.settings.CharacterSettings
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
import com.juice.ui.overlay.WritingBack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.juice.R
import com.juice.ui.liquid.JuiceLiquidView
import com.juice.core.progression.Progression
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import com.juice.ui.theme.FlavourTheme

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
    private val backdrop = mutableStateOf(Backdrop.SCRIM)
    // The blur radius the overlay window should have, which ramps up with the reveal.
    private var blurRadius = 0
    private val closeRequest = CloseRequest(reduceMotion = { settings.reduceMotion }, finish = ::closeOverlay)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sounds by lazy { SoundPoolOutput(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    @Volatile private var settings = JuiceSettings()
    private val characterStore by lazy { CharacterStore(this) }
    private val memoryStore by lazy { MemoryStore(File(filesDir, "memory.json")) }
    private val pendingCards by lazy { PendingCoachCardStore(File(filesDir, "pending-coach-cards.txt")) }
    private val conversationStore by lazy { ConversationStore(File(filesDir, "conversations.json")) }
    private val repository by lazy {
        ItemRepository(JuiceDatabase.open(this).itemDao(), hiddenCharacterIds = characterStore::disabledIds, hiddenChanges = characterStore.changes(),
            reminders = ReminderScheduler(this, java.time.Clock.systemDefaultZone()),
        )
    }
    private val createdStore by lazy { CreatedCharacterStore(this) }
    private val characters by lazy {
        CharacterRepository(
            characterStore, repository, conversationStore, memoryStore, createdStore,
            CharacterGenerator(llm = { hobby ->
                if (BuildConfig.GROQ_API_KEY.isBlank() || runBlocking { settingsRepository.current().onDeviceOnly }) {
                    throw IOException("Needs cloud access: turn off On-device only")
                }
                GroqClient(BuildConfig.GROQ_API_KEY).generatePersona(hobby)
            }),
        )
    }
    private val characterSettings by lazy { CharacterSettings(characters, scope, level = { Progression.levelFor(EconomyStore(this).load()?.state?.juice ?: 0L) }) }
    private val handleFeedback by lazy { FeedbackPlayer(sounds, VibratorOutput(this)) { settings.feedback } }
    private var openItems = 0
    // Room the overlay keeps clear for the system bars and cutout; follows the screen as it rotates.
    private val systemBars = mutableStateOf(PaddingValues(0.dp))
    // The screen's size and insets the handle and the overlay were last laid out for.
    private var screen: Pair<android.graphics.Rect, android.graphics.Insets>? = null
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
        screen = currentScreen()
        systemBars.value = systemBarsPadding()
        // A turn of 180 degrees changes no configuration, only which side the bars and cutout are on.
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(mainLooper))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenChanged()
    }

    private fun currentScreen(): Pair<android.graphics.Rect, android.graphics.Insets> {
        val metrics = windowManager.maximumWindowMetrics
        return metrics.bounds to metrics.windowInsets.getInsetsIgnoringVisibility(SAFE_INSETS)
    }

    /**
     * The screen rotated or changed size: the handle goes back to its saved place on the new
     * screen, kept clear of the bars and cutout, and the overlay keeps clear of them where they now are.
     */
    private fun onScreenChanged() {
        val now = currentScreen()
        if (now == screen) return
        screen = now
        systemBars.value = systemBarsPadding()
        handle?.let { view -> handleCentre().let { (x, y) -> view.moveTo(x, y) } }
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
                repository.repairCadences(LocalDate.now())
                repository.observeActive(LocalDate.now()).collect {
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

    /** Keeps [settings] current and moves or recolours the handle when its place or flavour changed. */
    private fun applySettings(next: JuiceSettings) {
        val moved = next.handleX != settings.handleX || next.handleY != settings.handleY
        val blurChanged = next.blur != settings.blur
        settings = next
        if (blurChanged) applyBackdrop()
        val view = handle ?: return
        styleHandle(view)
        if (moved) handleCentre().let { (x, y) -> view.moveTo(x, y) }
    }

    private fun handleCentre(): Pair<Float, Float> {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return settings.handleCentre(bounds.width(), bounds.height(), handleEdges())
    }

    /** How far in the handle's centre must sit: its window on screen, its logo clear of the bars and cutout. */
    private fun handleEdges(): HandleEdges {
        val density = resources.displayMetrics.density
        val insets = windowManager.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(SAFE_INSETS)
        return HandleEdges.of(
            SliceHandleView.EDGE_DP * density, SliceHandleView.LOGO_HALF_DP * density,
            insets.left, insets.top, insets.right, insets.bottom,
        )
    }

    /** The flavour the handle wears: the player's pick once unlocked, else the highest unlocked. */
    private fun styleHandle(view: SliceHandleView) {
        val juice = EconomyStore(this).load()?.state?.juice ?: 0L
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
            feedback = { handleFeedback.play(it) },
            onMoved = { x, y ->
                val bounds = windowManager.maximumWindowMetrics.bounds
                val edges = handleEdges()
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
        val bounds = windowManager.maximumWindowMetrics.bounds
        val target = backdropSliceSpot(bounds.width(), bounds.height(), resources.displayMetrics.density)
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

    /** Sets the window blur to follow the reveal [progress]; an unchanged radius is not re-applied. */
    private fun rampBlur(progress: Float) {
        if (overlayDialog == null) return
        val radius = blurRadiusFor(progress)
        if (radius == blurRadius) return
        // Tracked even under the scrim, so a later switch to blur starts from the right radius.
        blurRadius = radius
        applyBlur()
    }

    /**
     * The blur is the overlay window's own background blur, drawn by its render thread with each
     * frame. Unlike FLAG_BLUR_BEHIND it needs no window relayout, and no shared system dim layer
     * that the window manager drops whenever it judges the window not visible for a pass.
     */
    private fun applyBlur() {
        overlayDialog?.window?.setBackgroundBlurRadius(windowBlurRadius(backdrop.value, blurRadius))
    }

    /** Puts the failure on screen and its stack trace on the clipboard, since there is no logcat on the phone. */
    private fun reportOverlayFailure(e: Exception) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Juice error", Log.getStackTraceString(e)))
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
        val bounds = windowManager.maximumWindowMetrics.bounds
        val from = backdropSliceSpot(bounds.width(), bounds.height(), resources.displayMetrics.density)
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
        val reservoir = JuiceLiquidView(this)
        val effects = OverlayEffects()
        val feedback = FeedbackPlayer(sounds, VibratorOutput(this)) { settings.feedback }
        val session = GameSession(
            EconomyStore(this),
            feedback,
            onSatiated = {
                reservoir.emitParticles()
                reservoir.kick(3f)
                effects.banner(getString(R.string.overlay_banner_satiated))
            },
            onFlavourChanged = {
                reservoir.setFlavour(it.accent, it.deep)
                reservoir.viscous = it.viscous
                reservoir.fizz = it.fizz
            },
            onLevelUp = { level, unlocked ->
                scope.launch { unlockCharacters(level) }
                effects.banner(
                    if (unlocked == null) getString(R.string.overlay_banner_level, level)
                    else getString(R.string.overlay_banner_unlocked, level, unlocked.displayName),
                )
            },
            chosenFlavour = { settings.flavour },
        )
        session.open()
        scope.launch {
            unlockCharacters(session.level)
            characters.registry().all().forEach { characters.createCheckInIfDue(it) }
        }
        feedback.play(FeedbackEvent.OPEN)
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(this)
        val writingBack = WritingBack()
        val root = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(composeView, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        overlayLifecycle = lifecycleOwner
        val intake = CaptureIntake(
            repository,
            onDeviceOnly = { settingsRepository.current().onDeviceOnly },
            enqueue = { id, text -> IntakeWorker.enqueue(this, id, text) },
            cloudAvailable = { IntakeWorker.cloudAvailable },
        )
        val actions = OverlayActions(
            feedback = { feedback.play(it, boosted = session.tier == Tier.SATIATED) },
            onSettingsChange = { change -> scope.launch { settingsRepository.update(change) } },
            onClose = closeRequest::request,
            onScroll = { steps -> reservoir.kick(steps * 2.2f) },
            onLiquidTouch = { touch ->
                val x = touch.position.x / touch.size.width
                val y = touch.position.y / touch.size.height
                when (touch.phase) {
                    LiquidTouchPhase.DOWN -> reservoir.touchDown(x, y)
                    LiquidTouchPhase.MOVE -> reservoir.touchMove(x, y)
                    LiquidTouchPhase.UP -> reservoir.touchUp()
                }
            },
            onTick = session::tick,
            recapItems = repository::recapItems,
        )
        // Built and drawn at once, so the overlay never shows a bare frame; observe() brings the items in.
        run {
            val state = OverlayShellState(
                repository,
                scope,
                emptyList(),
                onCompleted = { item ->
                    val type = item.type.toEconomy()
                    val m = session.multiplier
                    val earned = session.complete(type)
                    reservoir.kick(2.4f + (EconomyConfig().fillPercent.getValue(type) / 25f).toFloat())
                    reservoir.emitParticles(10)
                    effects.float(if (m > 1.0) "+$earned x${multiplierLabel(m).dropLast(1)}" else "+$earned")
                },
                onCompletionUndone = { session.undo() },
                onCaptured = intake::submit,
                characters = characters,
                registry = characters.registry(),
                pendingCards = pendingCards,
                coachChatFor = { characterId, onFailed, onConfirmed ->
                    characters.registry().byId(characterId)?.takeUnless { characterId in characterStore.disabledIds() }?.let { character ->
                        val coachTasks = { runBlocking { repository.byCharacter(characterId).filter { it.type == ItemType.Routine } } }
                        val conversation = CoachConversation(
                            character,
                            BuildConfig.GROQ_API_KEY.takeIf { it.isNotBlank() }?.let { CoachClient(it) },
                            conversationStore,
                            memoryStore,
                            onDeviceOnly = { runBlocking { settingsRepository.current().onDeviceOnly } },
                            coachTasks = coachTasks,
                        )
                        CoachChatState(character, conversation, characters, scope, coachTasks, onFailed, onConfirmed, feedback = feedback)
                    }
                },
            )
            state.observe()
            state.restorePending()
            composeView.setContent {
                val current by settingsRepository.settings.collectAsState(settings)
                LaunchedEffect(current.flavour) { session.applyFlavour() }
                SideEffect {
                    reservoir.sloshEnabled = current.sloshEnabled
                    reservoir.particlesEnabled = current.particlesEnabled
                }
                val hud = session.hud()
                OverlayShell(
                    state,
                    settings = current,
                    hud = hud,
                    blurred = backdrop.value == Backdrop.BLUR,
                    onRevealProgress = ::rampBlur,
                    sliceShown = overlaySliceShown.value,
                    initialSpin = spinOnOpen,
                    initialAngle = angleOnOpen,
                    actions = actions,
                    effects = effects,
                    reservoir = { modifier -> ReservoirHost(reservoir, hud.fill, modifier) },
                    systemBars = systemBars.value,
                    writingBack = writingBack,
                    closing = closeRequest.closing.value,
                    onClosed = closeRequest::closed,
                    onDraining = ::startHandBack,
                    characters = characterSettings,
                )
            }
        }
        val dialog = overlayDialog(this, root) { writingBack.back(closeRequest::request) }
        // Compose looks for its owners from the window's root view, which is the dialog's decor.
        dialog.window!!.decorView.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }
        overlayDialog = dialog
        backdrop.value = chooseBackdrop()
        blurRadius = 0
        applyBlur()
        dialog.show()
        overlay = root
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
    }

    /** Unlocks every character whose level [level] has reached; each adds its meet card once. */
    private suspend fun unlockCharacters(level: Int) {
        characters.registry().all().forEach { characters.unlockAtLevel(it, level) }
        characters.createCreatorCardIfDue(level)
    }

    /** The system turns cross-window blur off and on (power saving, heat, some transitions); follow it. */
    private val blurListener = java.util.function.Consumer<Boolean> { applyBackdrop() }

    /**
     * Status bar, navigation bar and cutout sizes on every side, read from the display (when the
     * service starts and when the screen turns) rather than from inset callbacks, which reach an
     * overlay window late or not at all and made the layout jump. In landscape the navigation bar
     * and the cutout sit at the sides.
     */
    private fun systemBarsPadding(): PaddingValues {
        val insets = windowManager.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(SAFE_INSETS)
        val density = resources.displayMetrics.density
        return PaddingValues.Absolute(
            left = (insets.left / density).dp,
            top = (insets.top / density).dp,
            right = (insets.right / density).dp,
            bottom = (insets.bottom / density).dp,
        )
    }

    /** Blur behind the overlay when it is on, supported and battery saver is off; otherwise the scrim. */
    private fun applyBackdrop() {
        if (overlayDialog == null) return
        val next = chooseBackdrop()
        if (next == backdrop.value) return
        backdrop.value = next
        applyBlur()
    }

    private fun chooseBackdrop() = Backdrop.choose(
        blurEnabled = settings.blur,
        blurSupported = windowManager.isCrossWindowBlurEnabled,
        batterySaver = getSystemService(PowerManager::class.java).isPowerSaveMode,
    )

    private fun removeHandle() {
        handle?.dismiss()
        handle = null
    }

    private fun removeOverlay() {
        if (overlay != null) windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        overlayDialog?.dismiss()
        overlayDialog = null
        overlay = null
        handBack = null
        closeRequest.reset()
        overlayLifecycle?.stop()
        overlayLifecycle = null
    }

    companion object {
        private const val CHANNEL_ID = "juice_overlay"
        private const val NOTIFICATION_ID = 1
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private val SAFE_INSETS = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()

        /** Starts the service and opens the overlay, for a notification's PendingIntent.getForegroundService. */
        const val ACTION_OPEN = "com.juice.overlay.action.OPEN"

        fun openIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_OPEN)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
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

/** Embeds the [reservoir] view and pushes the meter's [fill] into it on every recomposition. */
@androidx.compose.runtime.Composable
internal fun ReservoirHost(reservoir: JuiceLiquidView, fill: Float, modifier: androidx.compose.ui.Modifier) {
    AndroidView(factory = { reservoir.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = modifier, update = { it.fill = fill })
}
