package com.juice.overlay

import java.time.LocalDate
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
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
import com.juice.ui.overlay.CoachChatState
import java.io.File
import kotlinx.coroutines.runBlocking
import com.juice.feedback.FeedbackEvent
import com.juice.ui.Tier
import com.juice.ui.overlay.OverlayActions
import com.juice.ui.overlay.OverlayEffects
import com.juice.ui.overlay.hud
import com.juice.ui.overlay.multiplierLabel
import com.juice.ui.overlay.toEconomy
import com.juice.core.character.Characters
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
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
import com.juice.ui.overlay.WritingBack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.juice.R
import com.juice.ui.reservoir.ThirstReservoirView
import com.juice.core.progression.Progression
import com.juice.ui.logo.SliceLogoPainter
import com.juice.ui.logo.SliceStyle
import com.juice.ui.theme.FlavourTheme

/** Foreground service owning the slice handle window and the full-screen overlay window. */
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var handle: SliceHandleView? = null
    private var overlay: View? = null
    private var starting = false
    private var overlayLifecycle: OverlayLifecycleOwner? = null
    private val backdrop = mutableStateOf(Backdrop.SCRIM)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sounds by lazy { SoundPoolOutput(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    @Volatile private var settings = JuiceSettings()
    private val repository by lazy { ItemRepository(JuiceDatabase.open(this).itemDao()) }
    private val characters by lazy { CharacterRepository(CharacterStore(this), repository) }
    private val handleFeedback by lazy { FeedbackPlayer(sounds, VibratorOutput(this)) { settings.feedback } }
    private var openItems = 0

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
        if (handle == null && overlay == null && !starting) {
            starting = true
            scope.launch {
                settings = settingsRepository.current()
                if (handle == null && overlay == null) showHandle()
                settingsRepository.settings.collect { applySettings(it) }
            }
            scope.launch {
                repository.observeActive(LocalDate.now()).collect {
                    openItems = it.size
                    handle?.slices = SliceLogoPainter.sliceCount(openItems)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
        return settings.handleCentre(bounds.width(), bounds.height())
    }

    /** The flavour the handle wears: the player's pick once unlocked, else the highest unlocked. */
    private fun styleHandle(view: SliceHandleView) {
        val juice = EconomyStore(this).load()?.state?.juice ?: 0L
        val level = Progression.levelFor(juice)
        val flavour = settings.flavour?.takeIf { it.unlockLevel <= level } ?: Progression.flavourFor(level)
        view.style = SliceStyle.of(flavour)
        view.accent = FlavourTheme.of(flavour).accent
    }

    private fun showHandle() {
        val density = resources.displayMetrics.density
        val view = SliceHandleView(
            this,
            windowManager,
            radius = { settings.swipeDistance.dp * density },
            reduceMotion = { settings.reduceMotion },
            feedback = { handleFeedback.play(it) },
            onMoved = { x, y ->
                val bounds = windowManager.maximumWindowMetrics.bounds
                val (fx, fy) = handleFractions(x, y, bounds.width(), bounds.height(), SliceHandleView.EDGE_DP * density)
                settings = settings.copy(handleX = fx, handleY = fy)
                scope.launch { settingsRepository.update { it.copy(handleX = fx, handleY = fy) } }
            },
            onOpen = ::swapToOverlay,
        )
        styleHandle(view)
        view.slices = SliceLogoPainter.sliceCount(openItems)
        val (x, y) = handleCentre()
        view.show(x, y)
        handle = view
    }

    private fun swapToOverlay() {
        removeHandle()
        guardedShowOverlay(::showOverlay, ::discardOverlay, ::showHandle, ::reportOverlayFailure)
    }

    /** Puts the failure on screen and its stack trace on the clipboard, since there is no logcat on the phone. */
    private fun reportOverlayFailure(e: Exception) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Juice error", Log.getStackTraceString(e)))
        Toast.makeText(this, getString(R.string.overlay_failed, e.javaClass.simpleName, e.message), Toast.LENGTH_LONG).show()
    }

    private fun discardOverlay() = runCatching { removeOverlay() }.onFailure { overlay = null }

    private fun closeOverlay() {
        removeOverlay()
        showHandle()
    }

    private fun showOverlay() {
        val reservoir = ThirstReservoirView(this)
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
                reservoir.color = it.reservoirColor
                reservoir.viscous = it.viscous
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
            Characters.all.forEach { characters.createCheckInIfDue(it) }
        }
        feedback.play(FeedbackEvent.OPEN)
        val bars = systemBarsPadding()
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(this)
        val writingBack = WritingBack()
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) writingBack.back(::closeOverlay)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            // The system can drop the blur behind an overlay without telling the blur listener
            // (seen after the overlay loses and regains focus); set it again on the way back.
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) applyBackdrop()
            }
        }.apply {
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
            onClose = ::closeOverlay,
            onScroll = { steps -> reservoir.kick(steps * 2.2f) },
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
                coachChatFor = { characterId, onFailed ->
                    Characters.byId(characterId)?.let { character ->
                        val coachTasks = { runBlocking { repository.byCharacter(characterId).filter { it.type == ItemType.Routine } } }
                        val conversation = CoachConversation(
                            character,
                            BuildConfig.GROQ_API_KEY.takeIf { it.isNotBlank() }?.let { CoachClient(it) },
                            ConversationStore(File(filesDir, "conversations.json")),
                            onDeviceOnly = { runBlocking { settingsRepository.current().onDeviceOnly } },
                            coachTasks = coachTasks,
                        )
                        CoachChatState(character, conversation, characters, scope, coachTasks, onFailed)
                    }
                },
            )
            state.observe()
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
                    actions = actions,
                    effects = effects,
                    reservoir = { modifier -> ReservoirHost(reservoir, hud.fill, modifier) },
                    systemBars = bars,
                    writingBack = writingBack,
                )
            }
        }
        val params = overlayLayoutParams()
        backdrop.value = chooseBackdrop()
        withBackdrop(params, backdrop.value)
        windowManager.addView(root, params)
        overlay = root
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
    }

    /** Unlocks every character whose level [level] has reached; each adds its meet card once. */
    private suspend fun unlockCharacters(level: Int) = Characters.all.forEach { characters.unlockAtLevel(it, level) }

    /** The system turns cross-window blur off and on (power saving, heat, some transitions); follow it. */
    private val blurListener = java.util.function.Consumer<Boolean> { applyBackdrop() }

    /**
     * Status and navigation bar heights, read once from the display rather than from inset
     * callbacks, which reach an overlay window late or not at all and made the layout jump.
     */
    private fun systemBarsPadding(): PaddingValues {
        val insets = windowManager.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
        )
        val density = resources.displayMetrics.density
        return PaddingValues(top = (insets.top / density).dp, bottom = (insets.bottom / density).dp)
    }

    /** Blur behind the overlay when it is on, supported and battery saver is off; otherwise the scrim. */
    private fun applyBackdrop() {
        val view = overlay ?: return
        backdrop.value = chooseBackdrop()
        val params = view.layoutParams as WindowManager.LayoutParams
        withBackdrop(params, backdrop.value)
        windowManager.updateViewLayout(view, params)
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
        overlay?.let(windowManager::removeView)
        overlay = null
        overlayLifecycle?.stop()
        overlayLifecycle = null
    }

    companion object {
        private const val CHANNEL_ID = "juice_overlay"
        private const val NOTIFICATION_ID = 1
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT

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

/** Blur behind the window for [Backdrop.BLUR], none otherwise. */
internal fun withBackdrop(params: WindowManager.LayoutParams, backdrop: Backdrop) {
    if (backdrop == Backdrop.BLUR) {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.blurBehindRadius = BLUR_RADIUS_PX
    } else {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        params.blurBehindRadius = 0
    }
}

private const val BLUR_RADIUS_PX = 40

/** Embeds the [reservoir] view and pushes the meter's [fill] into it on every recomposition. */
@androidx.compose.runtime.Composable
internal fun ReservoirHost(reservoir: ThirstReservoirView, fill: Float, modifier: androidx.compose.ui.Modifier) {
    AndroidView(factory = { reservoir }, modifier = modifier, update = { it.fill = fill })
}
