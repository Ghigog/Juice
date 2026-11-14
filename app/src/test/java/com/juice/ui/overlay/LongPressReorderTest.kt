package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LongPressReorderTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private lateinit var state: OverlayShellState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val events = mutableListOf<FeedbackEvent>()
    private var closed = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(vararg items: Item) {
        runBlocking { items.forEach { repository.save(it) } }
        state = OverlayShellState(repository, scope, items.toList(), today = LocalDate.of(2026, 10, 28), clock = CLOCK)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverlayShell(
                state,
                clock = CLOCK,
                settings = JuiceSettings(reduceMotion = true),
                actions = OverlayActions(feedback = { events += it }, onClose = { closed++ }),
            )
        }
        idle()
    }

    private fun idle() {
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun abc() = show(Item("a", ItemType.Task, "A"), Item("b", ItemType.Task, "B"), Item("c", ItemType.Task, "C"))

    private fun longPressCentre() {
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput { longClick() }
        idle()
    }

    @Test
    fun swipingRightOnTheCentreCardPicksNothingUp() {
        abc()
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput { swipeRight() }
        idle()
        assertEquals(null, state.held)
        assertEquals(emptyList<FeedbackEvent>(), events.filter { it == FeedbackEvent.SNAP })
    }

    @Test
    fun longPressPicksTheCentreCardUpWithSnap() {
        abc()
        longPressCentre()
        assertEquals("a", state.held)
        assertEquals(listOf(FeedbackEvent.SNAP), events.filter { it == FeedbackEvent.SNAP })
    }

    @Test
    fun longPressOnTheTitlePicksUpInsteadOfEditing() {
        abc()
        compose.onAllNodesWithText("A").onLast().performTouchInput { longClick() }
        idle()
        assertEquals("a", state.held)
        compose.onAllNodesWithTag(TAG_TITLE_FIELD).assertCountEquals(0)
    }

    @Test
    fun quickTapOnTheTitleStillEdits() {
        abc()
        compose.onAllNodesWithText("A").onLast().performTouchInput { click() }
        idle()
        compose.onNodeWithTag(TAG_TITLE_FIELD).assertExists()
        assertEquals(null, state.held)
    }

    @Test
    fun tappingTheCentreCardDropsTheHeldCardAtTheFocalCard() {
        abc()
        longPressCentre()
        state.moveTarget(2)
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput { click() }
        idle()
        assertEquals(null, state.held)
        assertEquals(listOf("b", "c", "a"), state.items.map { it.id })
        assertEquals(1, events.count { it == FeedbackEvent.CAPTURE })
        assertEquals(listOf("b", "c", "a"), runBlocking { repository.active(LocalDate.of(2026, 10, 28)).map { it.id } })
    }

    @Test
    fun tappingTheBackgroundWhileHeldPutsTheCardBackAndKeepsTheOverlayOpen() {
        abc()
        longPressCentre()
        compose.onNodeWithTag(TAG_CLOSE).performTouchInput { click(Offset(5f, 5f)) }
        idle()
        assertEquals(null, state.held)
        assertEquals(listOf("a", "b", "c"), state.items.map { it.id })
        assertEquals(0, closed)
    }
}
