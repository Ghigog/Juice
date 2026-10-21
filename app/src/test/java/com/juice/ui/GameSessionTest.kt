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
    private var now = 0L
    private var satiated = 0
    private val soundSets = mutableListOf<SoundSet>()
    private val themes = mutableListOf<FlavourTheme>()
    private val session = GameSession(
        store,
        object : Feedback {
            override fun play(event: FeedbackEvent) { events += event }
            override fun useSoundSet(soundSet: SoundSet) { soundSets += soundSet }
        },
        clock = { now },
        onSatiated = { satiated++ },
        onFlavourChanged = { themes += it },
    )

    @Test fun completingUpdatesJuiceMeterAndPlaysFeedback() {
        session.open()
        session.complete(ItemType.TASK)
        assertEquals(EconomyState(25, 12.0), session.state)
        assertEquals(listOf(FeedbackEvent.CHECKMARK), events)
        assertEquals(EconomyState(25, 12.0), store.load()?.state)
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
}
