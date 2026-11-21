package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import kotlin.test.assertNull
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
class SearchTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val clock = Clock.fixed(Instant.parse("2026-01-01T18:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: JuiceDatabase
    private lateinit var state: OverlayShellState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val feedback = mutableListOf<FeedbackEvent>()

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

    private fun show(vararg items: Item) {
        state = OverlayShellState(ItemRepository(db.itemDao()), scope, items.toList(), today = LocalDate.of(2026, 1, 1), clock = clock)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OverlayShell(
                state, clock = clock, settings = JuiceSettings(reduceMotion = true), hud = Hud(),
                actions = OverlayActions(feedback = { feedback += it }), writingBack = WritingBack(),
                onRevealProgress = {}, sliceShown = true, reservoir = {},
            )
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private val milk = Item("m", ItemType.Task, "Buy milk")
    private val sam = Item("s", ItemType.Task, "Call Sam")

    private fun SemanticsNodeInteraction.assertEditableTextEquals(expected: String) =
        assertEquals(expected, fetchSemanticsNode().config[SemanticsProperties.EditableText].text)

    private fun type(text: String) {
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput(text)
        settle()
    }

    @Test
    fun searchFiltersByTitleKeepsTheDraftAndCapturesNothing() {
        show(milk, sam)
        type("milk")
        compose.onNodeWithTag(TAG_SEARCH).performClick()
        settle()
        assertEquals("milk", state.filter.query)
        assertEquals(listOf("m"), state.shown.map { it.id })
        assertEquals(2, state.items.size)
        compose.onNodeWithTag(TAG_SEARCH_CHIP).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertEditableTextEquals("milk")
        assertEquals(listOf(FeedbackEvent.SNAP), feedback)
    }

    @Test
    fun searchWithABlankFieldDoesNothing() {
        show(milk, sam)
        compose.onNodeWithTag(TAG_SEARCH).performClick()
        type("  ")
        compose.onNodeWithTag(TAG_SEARCH).performClick()
        settle()
        assertNull(state.filter.query)
        compose.onAllNodesWithTag(TAG_SEARCH_CHIP).assertCountEquals(0)
        assertEquals(emptyList(), feedback)
    }

    @Test
    fun plusCapturesTheQueryAndClearsTheSearch() {
        show(milk, sam)
        type("milk")
        compose.onNodeWithTag(TAG_SEARCH).performClick()
        settle()
        compose.onNodeWithTag(TAG_ADD).performClick()
        settle()
        assertNull(state.filter.query)
        assertEquals(3, state.items.size)
        assertEquals(2, state.items.count { it.title == "milk" || it.title == "Buy milk" })
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertEditableTextEquals("")
        compose.onAllNodesWithTag(TAG_SEARCH_CHIP).assertCountEquals(0)
    }

    @Test
    fun tappingTheChipClearsTheSearch() {
        show(milk, sam)
        state.search("milk")
        settle()
        compose.onNodeWithTag(TAG_SEARCH_CHIP).performClick()
        settle()
        assertNull(state.filter.query)
        assertEquals(2, state.shown.size)
    }

    @Test
    fun searchAndFloatingTypesCombine() {
        show(milk, sam, Item("n", ItemType.Note, "Milk run"))
        state.floatType(ItemType.Task)
        state.search("milk")
        assertEquals(listOf("m"), state.shown.map { it.id })
    }

    @Test
    fun noMatchesShowsAFriendlyLine() {
        show(milk, sam)
        state.search("zzz")
        settle()
        compose.onNodeWithTag(TAG_OVERLAY_EMPTY).assertIsDisplayed().assertTextEquals("No cards match \"zzz\"")
    }
}
