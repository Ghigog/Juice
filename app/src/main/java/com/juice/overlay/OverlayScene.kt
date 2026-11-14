package com.juice.overlay

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.juice.AppContainer
import com.juice.R
import com.juice.core.card.CardOrder
import com.juice.core.economy.EconomyConfig
import com.juice.feedback.FeedbackEvent
import com.juice.feedback.FeedbackPlayer
import com.juice.intake.CaptureIntake
import com.juice.intake.IntakeWorker
import com.juice.settings.JuiceSettings
import com.juice.settings.CharacterSettings
import com.juice.ui.GameSession
import com.juice.ui.Tier
import com.juice.ui.overlay.OverlayActions
import com.juice.ui.overlay.OverlayEffects
import com.juice.ui.overlay.OverlayShell
import com.juice.ui.overlay.OverlayShellState
import com.juice.ui.overlay.WritingBack
import com.juice.ui.overlay.hud
import com.juice.ui.overlay.multiplierLabel
import com.juice.ui.overlay.toEconomy
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import kotlinx.coroutines.launch

/**
 * What one open overlay is made of: the game session, the liquid, the shell's state and the
 * actions it calls back, wired together. The service owns the window; this owns what is inside it.
 */
internal class OverlayScene(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val settings: () -> JuiceSettings,
    private val feedback: FeedbackPlayer,
    private val groqKey: GroqKeyActions,
    private val coachChats: CoachChats,
    private val closeRequest: CloseRequest,
    private val blur: OverlayBlur,
    private val screen: OverlayScreen,
    private val characterSettings: CharacterSettings,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val repository get() = container.items
    private val characters get() = container.characters
    private val settingsRepository get() = container.settings
    private val liquid = LiquidHost(context)
    private val effects = OverlayEffects()
    val writingBack = WritingBack()

    private val session = GameSession(
        container.economyStore,
        feedback,
        clock,
        onSatiated = {
            liquid.view.emitParticles()
            liquid.view.kick(3f)
            effects.banner(context.getString(R.string.overlay_banner_satiated))
        },
        onFlavourChanged = liquid::setFlavour,
        onLevelUp = { level, unlocked ->
            scope.launch { unlockCharacters(level) }
            effects.banner(
                if (unlocked == null) context.getString(R.string.overlay_banner_level, level)
                else context.getString(R.string.overlay_banner_unlocked, level, unlocked.displayName),
            )
        },
        chosenFlavour = { settings().flavour },
    )

    init {
        session.open()
        scope.launch {
            unlockCharacters(session.level)
            characters.registry().all().forEach { characters.createCheckInIfDue(it, clock.instant()) }
        }
        feedback.play(FeedbackEvent.OPEN)
        groqKey.loadMasked()
    }

    /** Unlocks every character whose level [level] has reached; each adds its meet card once. */
    private suspend fun unlockCharacters(level: Int) {
        characters.registry().all().forEach { characters.unlockAtLevel(it, level) }
        characters.createCreatorCardIfDue(level)
    }

    private fun actions(): OverlayActions {
        val reservoir = liquid.view
        return OverlayActions(
            feedback = { feedback.play(it, boosted = session.tier == Tier.SATIATED) },
            onSettingsChange = { change -> scope.launch { settingsRepository.update(change) } },
            onClose = closeRequest::request,
            onScroll = { steps -> reservoir.kick(steps * 2.2f) },
            onLiquidTouch = liquid::touch,
            onTick = session::tick,
            recapItems = repository::recapItems,
            shake = { reservoir.shake },
            onErupt = reservoir::erupt,
            groqKeyMasked = groqKey.masked,
            onSaveGroqKey = { key, onChecked ->
                groqKey.save(key, onSaved = { if (!settings().reduceMotion) reservoir.emitParticles(10) }, onChecked = onChecked)
            },
            onRemoveGroqKey = groqKey::remove,
        )
    }

    private fun shellState(): OverlayShellState {
        val intake = CaptureIntake(
            repository,
            onDeviceOnly = { settingsRepository.current().onDeviceOnly },
            enqueue = { id -> IntakeWorker.enqueue(context, id) },
            cloudAvailable = { container.groqKeyStore.hasKey.value },
        )
        return OverlayShellState(
            repository,
            scope,
            emptyList(),
            clock = clock,
            onCompleted = { item ->
                val type = item.type.toEconomy()
                val m = session.multiplier
                val earned = session.complete(type)
                liquid.view.kick(2.4f + (EconomyConfig().fillPercent.getValue(type) / 25f).toFloat())
                liquid.view.emitParticles(10)
                effects.float(if (m > 1.0) "+$earned x${multiplierLabel(m).dropLast(1)}" else "+$earned")
            },
            onCompletionUndone = { session.undo() },
            onCaptured = intake::submit,
            cardOrder = settings().cardOrder,
            onCustomOrder = { scope.launch { settingsRepository.update { it.copy(cardOrder = CardOrder.Custom) } } },
            characters = characters,
            registry = characters.registry(),
            pendingCards = container.pendingCards,
            coachChatFor = { id, onFailed, onConfirmed -> coachChats.chatFor(id, onFailed, onConfirmed, feedback) },
        )
    }

    /** Builds and draws the shell at once, so the overlay never shows a bare frame; observe() brings the items in. */
    fun setContent(
        view: ComposeView,
        sliceShown: MutableState<Boolean>,
        initialSpin: Float,
        initialAngle: Float,
        onDraining: () -> Unit,
    ) {
        val state = shellState()
        val actions = actions()
        state.observe()
        state.restorePending()
        view.setContent {
            val current by settingsRepository.settings.collectAsState(settings())
            LaunchedEffect(current.flavour) { session.applyFlavour() }
            LaunchedEffect(current.cardOrder) { state.setCardOrder(current.cardOrder) }
            SideEffect { liquid.sync(current) }
            val hud = session.hud()
            OverlayShell(
                state,
                settings = current,
                groqKeySaved = container.groqKeyStore.hasKey.collectAsState().value,
                hud = hud,
                blurred = blur.backdrop.value == Backdrop.BLUR,
                onRevealProgress = blur::ramp,
                sliceShown = sliceShown.value,
                initialSpin = initialSpin,
                initialAngle = initialAngle,
                actions = actions,
                effects = effects,
                reservoir = { modifier -> ReservoirHost(liquid.view, hud.fill, modifier) },
                systemBars = screen.systemBars.value,
                writingBack = writingBack,
                closing = closeRequest.closing.value,
                onClosed = closeRequest::closed,
                onDraining = onDraining,
                characters = characterSettings,
            )
        }
    }
}
