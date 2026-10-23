package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.feedback.FeedbackEvent
import com.juice.settings.JuiceSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecapScreenTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val events = mutableListOf<FeedbackEvent>()
    // Wednesday 2025-01-15 12:00 UTC.
    private val clock = Clock.fixed(Instant.parse("2025-01-15T12:00:00Z"), ZoneOffset.UTC)

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

    private fun show(items: List<Item>, reduceMotion: Boolean = false, writingBack: WritingBack = WritingBack()) {
        val state = OverlayShellState(ItemRepository(db.itemDao()), scope, emptyList())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverlayShell(
                state,
                settings = JuiceSettings(reduceMotion = reduceMotion),
                actions = OverlayActions(feedback = { events += it }, recapItems = { items }),
                writingBack = writingBack,
                clock = clock,
            )
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun open() {
        compose.onNodeWithTag(TAG_RECAP).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun done(id: String, type: ItemType, at: String, steps: List<Step> = emptyList()) = Item(
        id, type, "title-$id", status = ItemStatus.Completed, steps = steps,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"), completedAt = Instant.parse(at),
    )

    @Test
    fun theRecapButtonOpensTheSectionsInOrderAndRequestsTheOpenCue() {
        show(emptyList())
        open()
        compose.onNodeWithTag(TAG_RECAP_SCREEN).assertIsDisplayed()
        val tops = listOf("Today", "Week", "Month").map {
            compose.onNodeWithTag(TAG_RECAP_SECTION_PREFIX + it).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        assertEquals(FeedbackEvent.RECAP_OPEN, events.first())
    }

    @Test
    fun theCloseControlAndBackReturnToTheCarousel() {
        val writingBack = WritingBack()
        show(emptyList(), writingBack = writingBack)
        open()
        compose.onNodeWithTag(TAG_RECAP_CLOSE).performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onAllNodesWithTag(TAG_RECAP_SCREEN).assertCountEquals(0)

        open()
        var closedOverlay = false
        compose.runOnIdle { writingBack.back { closedOverlay = true } }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onAllNodesWithTag(TAG_RECAP_SCREEN).assertCountEquals(0)
        assertEquals(false, closedOverlay)
    }

    @Test
    fun aSectionWithoutCompletionsShowsZeroAndAnEmptyMessage() {
        show(emptyList())
        open()
        compose.onNodeWithTag(TAG_RECAP_COUNT_PREFIX + "Today").assertTextEquals("0")
        compose.onNodeWithTag(TAG_RECAP_EMPTY_PREFIX + "Today").assertIsDisplayed()
    }

    @Test
    fun reduceMotionShowsFinalCountsAtOnce() {
        show(listOf(done("a", ItemType.Task, "2025-01-15T09:00:00Z")), reduceMotion = true)
        open()
        compose.onNodeWithTag(TAG_RECAP_COUNT_PREFIX + "Today").assertTextEquals("1")
    }

    @Test
    fun countersTickUpWhenMotionIsOn() {
        show(listOf(done("a", ItemType.Task, "2025-01-15T09:00:00Z")))
        open()
        compose.onNodeWithTag(TAG_RECAP_COUNT_PREFIX + "Today").assertTextEquals("0")
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag(TAG_RECAP_COUNT_PREFIX + "Today").assertTextEquals("1")
    }

    @Test
    fun projectsAreListedFirstWithTheirTickedStepsAndBurndownStartsAtTheOpenCount() {
        val step = Step("s", "ticked-step", done = true, doneAt = Instant.parse("2025-01-15T08:00:00Z"))
        show(
            listOf(
                done("n", ItemType.Note, "2025-01-15T07:00:00Z"),
                done("p", ItemType.Project, "2025-01-15T09:00:00Z", listOf(step)),
                Item("open", ItemType.Task, "open", createdAt = Instant.parse("2025-01-01T00:00:00Z")),
            ),
            reduceMotion = true,
        )
        open()
        compose.onAllNodesWithText("✓ ticked-step").onFirst().assertExists()
        val project = compose.onAllNodesWithText("title-p").onFirst().fetchSemanticsNode().boundsInRoot.top
        val note = compose.onAllNodesWithText("title-n").onFirst().fetchSemanticsNode().boundsInRoot.top
        assert(project < note)
        // 1 open task plus the 2 completed before today ended: 3 open at the start of the day.
        compose.onAllNodesWithText("3 open at the start").onFirst().assertExists()
    }

    @Test
    fun eachSectionPlaysTheRevealCue() {
        show(emptyList())
        open()
        assert(events.count { it == FeedbackEvent.RECAP_REVEAL } >= 1)
    }
}
