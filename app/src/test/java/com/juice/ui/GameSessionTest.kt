package com.juice.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.economy.EconomyState
import com.juice.core.economy.ItemType
import com.juice.data.EconomyStore
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
    private val session = GameSession(
        store,
        object : Feedback { override fun play(event: FeedbackEvent) { events += event } },
        clock = { now },
        onSatiated = { satiated++ },
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
}
