package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TypeFilterTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val clock = Clock.fixed(Instant.parse("2026-01-01T18:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: JuiceDatabase
    private lateinit var state: OverlayShellState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val feedback = mutableListOf<FeedbackEvent>()

    private val task = Item("t", ItemType.Task, "Task one")
    private val note = Item("n", ItemType.Note, "Note one")
    private val goal = Item("g", ItemType.Goal, "Goal one")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JuiceDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(vararg items: Item, reduceMotion: Boolean = true) {
        state = OverlayShellState(ItemRepository(db.itemDao()), scope, items.toList(), today = LocalDate.of(2026, 1, 1), clock = clock)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CardRow(
                state, Hud(), JuiceSettings(reduceMotion = reduceMotion), true, OverlayActions(feedback = { feedback += it }),
                OverlayEffects(), { Modifier }, 0, {}, Modifier,
            )
        }
        settle()
    }

    private fun settle(millis: Long = 2_000) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun floating(type: ItemType) = compose.onAllNodesWithTag(TAG_FLOATING_TYPE_PREFIX + type.name)

    @Test
    fun doubleTappingThePillFloatsTheTypeAndShowsOnlyThatType() {
        show(task, note, goal)
        compose.onNodeWithTag(TAG_TYPE_PILL).performTouchInput { doubleClick() }
        settle()
        assertEquals(setOf(ItemType.Task), state.filter.types)
        assertEquals(listOf("t"), state.shown.map { it.id })
        floating(ItemType.Task).assertCountEquals(1)
        assertTrue(FeedbackEvent.SNAP in feedback)
    }

    @Test
    fun aSingleTapStillOpensTheTypeMenuOnceTheDoubleTapTimeoutPasses() {
        show(task, note)
        compose.onNodeWithTag(TAG_TYPE_PILL).performClick()
        settle()
        compose.onNodeWithTag(TAG_TYPE_OPTION_PREFIX + "Note").assertIsDisplayed()
        assertFalse(state.filter.isActive)
    }

    @Test
    fun floatingASecondTypeKeepsTheFirst() {
        show(task, note, goal)
        state.floatType(ItemType.Task)
        settle()
        assertTrue(state.floatType(ItemType.Note))
        settle()
        assertEquals(listOf("t", "n"), state.shown.map { it.id }.sorted().sortedBy { if (it == "t") 0 else 1 })
        floating(ItemType.Task).assertCountEquals(1)
        floating(ItemType.Note).assertCountEquals(1)
    }

    @Test
    fun tappingAFloatingTypeReleasesItAndTheRowCollapsesWhenEmpty() {
        show(task, note, goal)
        state.floatType(ItemType.Task)
        state.floatType(ItemType.Note)
        settle()
        compose.onNodeWithTag(TAG_FLOATING_TYPE_PREFIX + "Task").performClick()
        settle()
        assertEquals(setOf(ItemType.Note), state.filter.types)
        assertEquals(listOf("n"), state.shown.map { it.id })
        floating(ItemType.Task).assertCountEquals(0)
        compose.onNodeWithTag(TAG_FLOATING_TYPE_PREFIX + "Note").performClick()
        settle()
        assertEquals(3, state.shown.size)
        floating(ItemType.Note).assertCountEquals(0)
        assertEquals(0, compose.onNodeWithTag(TAG_TYPE_FILTER_ROW).fetchSemanticsNode().size.height)
        assertEquals(listOf(FeedbackEvent.UNDO, FeedbackEvent.UNDO), feedback)
    }

    @Test
    fun motionOnStillShowsAndReleasesTheFloatingType() {
        show(task, note, reduceMotion = false)
        state.floatType(ItemType.Task)
        settle()
        floating(ItemType.Task).assertCountEquals(1)
        compose.onNodeWithTag(TAG_FLOATING_TYPE_PREFIX + "Task").performClick()
        settle()
        floating(ItemType.Task).assertCountEquals(0)
    }

    @Test
    fun reorderingIsOffWhileAFilterIsActive() {
        show(task, note)
        state.floatType(ItemType.Task)
        state.pickUp(task)
        assertEquals(null, state.held)
        state.releaseType(ItemType.Task)
        state.pickUp(task)
        assertEquals("t", state.held)
    }
}
