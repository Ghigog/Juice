package com.juice.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.economy.EconomyState
import com.juice.core.economy.ItemType
import com.juice.data.EconomyStore
import com.juice.core.progression.Flavour
import com.juice.core.progression.Progression
import com.juice.ui.theme.FlavourTheme
import com.juice.ui.theme.SoundSet
import com.juice.feedback.Feedback
import com.juice.feedback.FeedbackEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class GameSessionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = EconomyStore(context)
    private val events = mutableListOf<FeedbackEvent>()
    private val completionMultipliers = mutableListOf<Double>()
    private var now = 0L
    private var satiated = 0
    private val soundSets = mutableListOf<SoundSet>()
    private val themes = mutableListOf<FlavourTheme>()
    private val session = GameSession(
        store,
        object : Feedback {
            override fun play(event: FeedbackEvent, boosted: Boolean) { events += event }
            override fun playCompletion(multiplier: Double) { completionMultipliers += multiplier; events += FeedbackEvent.CHECKMARK }
            override fun useSoundSet(soundSet: SoundSet) { soundSets += soundSet }
        },
        clock = { now },
        onSatiated = { satiated++ },
        onFlavourChanged = { themes += it },
        onLevelUp = { level, unlocked -> levelUps += level to unlocked },
        chosenFlavour = { chosen },
    )
    private val levelUps = mutableListOf<Pair<Int, Flavour?>>()
    private var chosen: Flavour? = null

    @Test fun completionIsPlayedWithTheMultiplierBeforeIt() {
        session.open()
        val before = session.multiplier
        session.complete(ItemType.PROJECT)
        val afterFirst = session.multiplier
        session.complete(ItemType.NOTE)
        assertEquals(listOf(before, afterFirst), completionMultipliers)
    }

    @Test fun completingUpdatesJuiceMeterAndPlaysFeedback() {
        session.open()
        session.complete(ItemType.TASK)
        assertEquals(EconomyState(25, 12.0), session.state)
        assertEquals(listOf(FeedbackEvent.CHECKMARK), events)
        assertEquals(EconomyState(25, 12.0), store.load()?.state)
    }

    @Test fun crossingTheLevelFiveThresholdReportsLevelFive() {
        store.save(EconomyState(juice = Progression.thresholdFor(5) - 1, meter = 50.0), 0L)
        session.open()
        session.complete(ItemType.TASK)
        assertEquals(5, levelUps.last().first)
    }

    @Test fun reopeningAfterTenMinutesDrainsTenPercent() {
        session.open()
        session.complete(ItemType.GOAL) // meter 30
        now = 10 * 60_000L
        session.open()
        assertEquals(20.0, session.state.meter, 0.01)
        assertEquals(100, session.state.juice)
    }

    @Test fun undoRevertsJuiceAndFill() {
        session.open()
        session.complete(ItemType.TASK)
        session.undo()
        assertEquals(EconomyState(0, 0.0), session.state)
    }

    @Test fun crossingIntoSatiatedTriggersParticlesAndPeakFeedback() {
        session.open()
        session.complete(ItemType.PROJECT) // 50
        session.complete(ItemType.PROJECT) // 100
        assertEquals(1, satiated)
        assertEquals(1, events.count { it == FeedbackEvent.SATIATED_PEAK })
    }

    @Test fun newPlayerOpensWithElectricCitrus() {
        session.open()
        assertEquals(1, session.level)
        assertEquals(FlavourTheme.of(Flavour.ELECTRIC_CITRUS), themes.single())
        assertEquals(listOf(SoundSet.BRIGHT_CLICKS_AND_POPS), soundSets)
    }

    @Test fun crossingLevelFiveSwitchesToBerrySurge() {
        store.save(EconomyState(juice = Progression.thresholdFor(5) - 1), 0)
        session.open()
        assertEquals(Flavour.ELECTRIC_CITRUS, session.flavour)
        session.complete(ItemType.TASK)
        assertEquals(5, session.level)
        assertEquals(FlavourTheme.of(Flavour.BERRY_SURGE), themes.last())
        assertEquals(SoundSet.SUB_BASS_AND_FIZZ, soundSets.last())
    }

    @Test fun levelUpReportsTheFlavourItUnlocks() {
        store.save(EconomyState(juice = Progression.thresholdFor(5) - 1), 0)
        session.open()
        session.complete(ItemType.TASK)
        assertEquals(listOf<Pair<Int, Flavour?>>(5 to Flavour.BERRY_SURGE), levelUps)
        assertEquals(FeedbackEvent.LEVEL_UP, events.last())
    }

    @Test fun aChosenFlavourAppliesOnlyOnceUnlocked() {
        chosen = Flavour.GOLDEN_HONEY
        session.open()
        assertEquals(Flavour.ELECTRIC_CITRUS, session.flavour)
        store.save(EconomyState(juice = Progression.thresholdFor(25)), 0)
        session.open()
        assertEquals(Flavour.GOLDEN_HONEY, session.flavour)
        chosen = Flavour.BERRY_SURGE
        session.applyFlavour()
        assertEquals(FlavourTheme.of(Flavour.BERRY_SURGE), themes.last())
    }

    @Test fun meterDrainsWhileOpen() {
        session.open()
        session.complete(ItemType.GOAL) // 30
        now = 5 * 60_000L
        session.tick()
        assertEquals(25.0, session.state.meter, 0.01)
        assertEquals(Tier.THIRSTY, session.tier)
    }

    @Test fun levelProgressRunsThroughTheLevel() {
        store.save(EconomyState(juice = 150), 0) // level 2 spans 100..400
        session.open()
        assertEquals(2, session.level)
        assertEquals(50f / 300f, session.levelProgress, 0.001f)
    }
}
