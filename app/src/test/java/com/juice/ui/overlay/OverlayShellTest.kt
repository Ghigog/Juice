package com.juice.ui.overlay

import com.juice.RegisterComposeActivity
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.model.Step
import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.LocalDate
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
import com.juice.ui.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.onRoot

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OverlayShellTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private lateinit var state: OverlayShellState
    private val writingBack = WritingBack()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            // Run queries where they are called, so a save has finished before the test looks.
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = ItemRepository(db.itemDao())
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(
        vararg items: Item,
        hud: Hud = Hud(),
        actions: OverlayActions = OverlayActions(),
        reduceMotion: Boolean = true,
        onRevealProgress: (Float) -> Unit = {},
    ) {
        state = OverlayShellState(repository, scope, items.toList())
        // The clock only moves when a test moves it, so the 10 second undo window holds until then.
        compose.mainClock.autoAdvance = false
        compose.setContent { OverlayShell(state, settings = JuiceSettings(reduceMotion = reduceMotion), hud = hud, actions = actions, writingBack = writingBack, onRevealProgress = onRevealProgress) }
        idle()
    }

    private fun juiceBoxTop() = compose.onNodeWithTag(TAG_JUICE_BOX).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun withMotionOnTheContentRisesInAfterTheReveal() {
        show(Item("a", ItemType.Task, "Call Sam"), reduceMotion = false)
        val beforeEntrance = juiceBoxTop()
        compose.mainClock.advanceTimeBy(REVEAL_MILLIS.toLong() + ENTRANCE_TOTAL_MILLIS + 100)
        compose.waitForIdle()
        assertTrue(beforeEntrance > juiceBoxTop())
    }

    @Test
    fun withMotionOnTheRevealReportsRisingProgressFromZeroToOne() {
        val reported = mutableListOf<Float>()
        show(Item("a", ItemType.Task, "Call Sam"), reduceMotion = false, onRevealProgress = { reported += it })
        compose.mainClock.advanceTimeBy(REVEAL_MILLIS.toLong() + 100)
        compose.waitForIdle()
        assertEquals(0f, reported.first(), 0.001f)
        assertEquals(1f, reported.last(), 0.001f)
        assertTrue(reported.size > 2)
        assertEquals(reported.sorted(), reported)
    }

    @Test
    fun withReducedMotionTheRevealReportsOneAtOnce() {
        val reported = mutableListOf<Float>()
        show(Item("a", ItemType.Task, "Call Sam"), reduceMotion = true, onRevealProgress = { reported += it })
        assertEquals(listOf(1f), reported)
    }

    @Test
    fun theCardsAreClippedRoundAtEveryFrameOfTheEntrance() {
        show(Item("a", ItemType.Task, "Call Sam"), Item("b", ItemType.Task, "Email Lee"), reduceMotion = false)
        val clipped = SemanticsMatcher.keyIsDefined(ClippedShape)
        for (step in listOf(0L, 150L, REVEAL_MILLIS.toLong(), ENTRANCE_TOTAL_MILLIS.toLong())) {
            compose.mainClock.advanceTimeBy(step)
            val cards = compose.onAllNodes(clipped, useUnmergedTree = true)
            cards.assertCountEquals(3)
            cards.fetchSemanticsNodes().forEach { assertTrue(it.config[ClippedShape] is RoundedCornerShape) }
        }
    }

    @Test
    fun withReducedMotionTheContentIsAtRestAtOnce() {
        show(Item("a", ItemType.Task, "Call Sam"), reduceMotion = true)
        val first = juiceBoxTop()
        compose.mainClock.advanceTimeBy(REVEAL_MILLIS.toLong() + ENTRANCE_TOTAL_MILLIS + 100)
        compose.waitForIdle()
        assertEquals(first, juiceBoxTop(), 0.5f)
    }

    @Test
    fun tappingAndDraggingTheCentreCardReportsLiquidTouches() {
        val touches = mutableListOf<LiquidTouch>()
        show(Item("a", ItemType.Task, "Call Sam"), actions = OverlayActions(onLiquidTouch = { touches += it }))
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            moveTo(Offset(width * 0.8f, height * 0.5f))
            up()
        }
        idle()
        assertEquals(listOf(LiquidTouchPhase.DOWN, LiquidTouchPhase.MOVE, LiquidTouchPhase.UP), touches.map { it.phase }.distinct())
        assertEquals(touches.first().size.width * 0.2f, touches.first().position.x, 1f)
        assertTrue(touches.first { it.phase == LiquidTouchPhase.MOVE }.position.x > touches.first().position.x)
    }

    @Test
    fun drawingStillCompletesAndDismissesWhileLiquidTouchesAreReported() {
        val item = Item("a", ItemType.Task, "Call Sam")
        runBlocking { repository.save(item) }
        show(item, actions = OverlayActions(onLiquidTouch = {}))
        drawCheckmark()
        assertEquals(ItemStatus.Completed, statusOf("a"))
    }

    @Test
    fun aCrossStillDismissesWhileLiquidTouchesAreReported() {
        val item = Item("a", ItemType.Task, "Call Sam")
        runBlocking { repository.save(item) }
        show(item, actions = OverlayActions(onLiquidTouch = {}))
        drawCross()
        assertEquals(ItemStatus.Archived, statusOf("a"))
    }

    @Test
    fun aRoutineCardShowsItsTypeAndCadence() {
        show(Item("r", ItemType.Routine, "Water plants", cadence = Cadence(setOf(DayOfWeek.SUNDAY))))
        compose.onAllNodesWithText("Every Sun").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Routine", ignoreCase = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun theRoutinesButtonListsARoutineNotDueTodayAndBackReturns() {
        val later = Item(
            "l", ItemType.Routine, "Stretch",
            cadence = Cadence(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)), nextDueOn = LocalDate.now().plusDays(3),
        )
        runBlocking { repository.save(later) }
        show()
        compose.onAllNodesWithTag(TAG_ROUTINES_SCREEN).assertCountEquals(0)
        compose.onNodeWithTag(TAG_ROUTINES).performClick()
        idle()
        idle()
        compose.onNodeWithTag(TAG_ROUTINES_SCREEN).assertIsDisplayed()
        compose.onAllNodesWithText("Stretch").onFirst().assertIsDisplayed()

        var closedOverlay = false
        compose.runOnIdle { writingBack.back { closedOverlay = true } }
        idle()
        compose.onAllNodesWithTag(TAG_ROUTINES_SCREEN).assertCountEquals(0)
        assertEquals(false, closedOverlay)
    }

    @Test
    fun theRoutinesButtonWithNoRoutinesShowsTheEmptyMessage() {
        show()
        compose.onNodeWithTag(TAG_ROUTINES).performClick()
        idle()
        idle()
        compose.onNodeWithTag(TAG_ROUTINES_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ROUTINES_EMPTY).assertIsDisplayed()
    }

    @Test
    fun reopeningTheRoutinesViewShowsEachRoutineOnce() {
        runBlocking {
            repository.save(Item("a", ItemType.Routine, "Stretch", cadence = Cadence(setOf(DayOfWeek.TUESDAY)), nextDueOn = LocalDate.now().plusDays(3)))
        }
        show()
        repeat(2) {
            compose.onNodeWithTag(TAG_ROUTINES).performClick()
            idle()
            idle()
            compose.onNodeWithTag(TAG_ROUTINES_SCREEN).assertIsDisplayed()
            assertEquals(listOf("a"), state.routines.items.map { it.id })
            compose.onNodeWithTag(TAG_ROUTINES_CLOSE).performClick()
            idle()
            compose.onAllNodesWithTag(TAG_ROUTINES_SCREEN).assertCountEquals(0)
        }
    }

    private fun idle() {
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun statusOf(id: String) = runBlocking { repository.get(id)?.status }

    /** Lets the stroke pause and the card's fly-off run, so the drawing has been acted on. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(STROKE_PAUSE_MILLIS + FLY_MILLIS + 200L)
        idle()
    }

    private fun drawCheckmark() {
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
            moveTo(Offset(width * 0.4f, height * 0.7f))
            moveTo(Offset(width * 0.8f, height * 0.3f))
            up()
        }
        settle()
    }

    /** Two strokes, as people draw an X. */
    private fun drawCross() {
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
            down(Offset(width * 0.2f, height * 0.2f))
            moveTo(Offset(width * 0.8f, height * 0.8f))
            up()
            down(Offset(width * 0.8f, height * 0.2f))
            moveTo(Offset(width * 0.2f, height * 0.8f))
            up()
        }
        settle()
    }

    @Test
    fun reservoirIsBehindTheCentreCard() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_RESERVOIR, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasTestTag(TAG_RESERVOIR) and hasAnyAncestor(hasTestTag(TAG_CENTRE_CARD)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyCardStillShowsTheReservoir() {
        show()
        compose.onNode(hasTestTag(TAG_RESERVOIR) and hasAnyAncestor(hasTestTag(TAG_CENTRE_CARD)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun multiplierBadgeIsInTheHeader() {
        show(hud = Hud(multiplier = 1.5))
        compose.onNodeWithTag(TAG_MULTIPLIER).assertIsDisplayed()
        compose.onNode(hasText("1.5x") and hasAnyAncestor(hasTestTag(TAG_MULTIPLIER)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun hydrationLabelShowsTierAndPercent() {
        show(hud = Hud(fill = 0.37f, tier = Tier.HYDRATED))
        compose.onNodeWithTag(TAG_HYDRATION).assertTextEquals("HYDRATED: 37%")
    }

    @Test
    fun juiceBoxFillsTheContentWidthWithNoCards() {
        show()
        val box = compose.onNodeWithTag(TAG_JUICE_BOX).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag(TAG_HYDRATION).fetchSemanticsNode().boundsInRoot
        assertEquals(label.left, box.left, 0.5f)
        assertTrue(label.bottom <= box.top + 0.5f)
        // Content has 14dp padding each side, so the box starts at the padding and nothing sits before it.
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.width - 2 * box.left, box.width, 0.5f)
    }

    @Test
    fun hydrationLabelAlignsToJuiceBoxWithCards() {
        show(Item("a", ItemType.Task, "Call Sam"))
        val box = compose.onNodeWithTag(TAG_JUICE_BOX).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag(TAG_HYDRATION).fetchSemanticsNode().boundsInRoot
        assertEquals(box.left, label.left, 0.5f)
        assertTrue(box.left > 14f)
    }

    @Test
    fun capturedTextAppearsAsSavedNote() {
        show()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("Buy milk")
        compose.onNodeWithTag(TAG_ADD).performClick()
        idle()
        compose.onAllNodesWithText("Buy milk").onFirst().assertIsDisplayed()
        idle()
        val saved = runBlocking { repository.active() }
        assertEquals(listOf("Buy milk"), saved.map { it.title })
        assertEquals(ItemType.Note, saved.single().type)
    }

    @Test
    fun focusingCaptureFieldEntersWritingModeAndSubmitLeavesIt() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performClick()
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertDoesNotExist()
        compose.onNodeWithTag(TAG_RESERVOIR).assertDoesNotExist()
        compose.onNodeWithTag(TAG_HYDRATION).assertDoesNotExist()
        compose.onNodeWithTag(TAG_ADD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CLOSE).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("line one\nline two")
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertDoesNotExist()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertTextContains("line one\nline two")
        compose.onNodeWithTag(TAG_ADD).performClick()
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_RESERVOIR).assertIsDisplayed()
        compose.onNodeWithTag(TAG_HYDRATION).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertTextEquals("Enter new note or task…", includeEditableText = false)
    }

    @Test
    fun tappingOutsideTheFieldLeavesWritingModeAndKeepsDraft() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("draft")
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertDoesNotExist()
        compose.onRoot().performTouchInput { click(Offset(width / 2f, 40f)) }
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertTextContains("draft")
    }

    @Test
    fun backLeavesWritingModeAndKeepsDraft() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("draft")
        idle()
        var closed = false
        compose.runOnUiThread { writingBack.back { closed = true } }
        idle()
        assertEquals(false, closed)
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertTextContains("draft")
    }

    @Test
    fun tappingEmptyBackgroundCloses() {
        var closed = 0
        show(Item("a", ItemType.Task, "Call Sam"), actions = OverlayActions(onClose = { closed++ }))
        compose.onNodeWithTag(TAG_CLOSE).performTouchInput { click(Offset(width * 0.5f, height - 2f)) }
        idle()
        assertEquals(1, closed)
    }

    private fun threeItems() = arrayOf(
        Item("a", ItemType.Task, "Call Sam"), Item("b", ItemType.Task, "Buy milk"), Item("c", ItemType.Task, "Pay rent"),
    )

    @Test
    fun tappingCarouselPaddingAboveAndBelowTheCardsCloses() {
        var closed = 0
        show(*threeItems(), actions = OverlayActions(onClose = { closed++ }))
        compose.onNodeWithTag(TAG_CAROUSEL).performTouchInput { click(Offset(56.dp.toPx(), 4f)) }
        idle()
        assertEquals(1, closed)
        compose.onNodeWithTag(TAG_CAROUSEL).performTouchInput { click(Offset(56.dp.toPx(), height - 4f)) }
        idle()
        assertEquals(2, closed)
    }

    @Test
    fun tappingTheGapBetweenCarouselCardsKeepsOverlayOpen() {
        var closed = 0
        show(*threeItems(), actions = OverlayActions(onClose = { closed++ }))
        // The focal card is centred, 56dp tall; the gap to the next card is 8dp below it.
        compose.onNodeWithTag(TAG_CAROUSEL).performTouchInput { click(Offset(56.dp.toPx(), height / 2f + 32.dp.toPx())) }
        idle()
        assertEquals(0, closed)
    }

    @Test
    fun gapCheckOnlyCoversTheSpaceBetweenItems() {
        val spans = listOf(10..50, 60..100)
        assertTrue(tapIsInCarouselGap(55f, spans))
        assertEquals(false, tapIsInCarouselGap(30f, spans))
        assertEquals(false, tapIsInCarouselGap(5f, spans))
        assertEquals(false, tapIsInCarouselGap(120f, spans))
        assertEquals(false, tapIsInCarouselGap(5f, emptyList()))
    }

    @Test
    fun tappingACarouselCardKeepsOverlayOpen() {
        var closed = 0
        show(*threeItems(), actions = OverlayActions(onClose = { closed++ }))
        compose.onNodeWithTag(TAG_CAROUSEL).performTouchInput { click(Offset(56.dp.toPx(), height / 2f)) }
        idle()
        assertEquals(0, closed)
    }

    @Test
    fun tappingACardKeepsOverlayOpen() {
        var closed = 0
        show(Item("a", ItemType.Task, "Call Sam"), actions = OverlayActions(onClose = { closed++ }))
        compose.onNodeWithTag(TAG_CENTRE_CARD).performClick()
        idle()
        assertEquals(0, closed)
    }

    @Test
    fun backOutsideWritingModeCloses() {
        show(Item("a", ItemType.Task, "Call Sam"))
        var closed = false
        compose.runOnUiThread { writingBack.back { closed = true } }
        assertEquals(true, closed)
    }

    @Test
    fun blankDraftStaysInWritingMode() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("  ")
        compose.onNodeWithTag(TAG_ADD).performClick()
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertDoesNotExist()
        assertEquals(listOf("Call Sam"), state.items.map { it.title })
    }

    @Test
    fun checkmarkCompletesItemAndOffersUndo() {
        show(Item("a", ItemType.Task, "Call Sam"))
        drawCheckmark()
        idle()
        compose.onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        assertEquals(ItemStatus.Completed, statusOf("a"))
    }

    private fun drawOnStep(id: String, check: Boolean) {
        compose.onNodeWithTag(stepTag(id), useUnmergedTree = true).performTouchInput {
            if (check) {
                down(Offset(width * 0.1f, height * 0.5f))
                moveTo(Offset(width * 0.2f, height * 0.9f))
                moveTo(Offset(width * 0.4f, height * 0.1f))
                up()
            } else {
                down(Offset(width * 0.1f, height * 0.1f))
                moveTo(Offset(width * 0.3f, height * 0.9f))
                up()
                down(Offset(width * 0.3f, height * 0.1f))
                moveTo(Offset(width * 0.1f, height * 0.9f))
                up()
            }
        }
        settle()
    }

    private val project = Item("p", ItemType.Project, "Move", steps = listOf(Step("a", "Pack"), Step("b", "Book van")))

    @Test
    fun checkOnAStepMarksItDoneAndKeepsTheProject() {
        show(project)
        drawOnStep("a", check = true)
        assertEquals(listOf(true, false), state.items.single().steps.map { it.done })
        assertEquals(ItemStatus.Active, statusOf("p"))
    }

    @Test
    fun crossOnAStepRemovesIt() {
        show(project)
        drawOnStep("a", check = false)
        assertEquals(listOf("Book van"), state.items.single().steps.map { it.title })
    }

    @Test
    fun crossDismissesAndUndoRestores() {
        val item = Item("a", ItemType.Task, "Call Sam")
        runBlocking { repository.save(item) }
        show(item)
        drawCross()
        idle()
        assertEquals(ItemStatus.Archived, statusOf("a"))
        compose.onNodeWithTag(TAG_UNDO).performClick()
        idle()
        compose.onAllNodesWithText("Call Sam").onFirst().assertIsDisplayed()
        assertEquals(ItemStatus.Active, statusOf("a"))
    }

    @Test
    fun undoDisappearsAfterTenSeconds() {
        val item = Item("a", ItemType.Task, "Call Sam")
        show(item)
        drawCross()
        idle()
        compose.mainClock.advanceTimeBy(OverlayShellState.UNDO_WINDOW_MILLIS + 100)
        compose.onNodeWithTag(TAG_UNDO).assertDoesNotExist()
    }

    @Test
    fun tappingTheTypePillChangesTheCardAndSavesIt() {
        val item = Item("a", ItemType.Note, "Call Sam")
        runBlocking { repository.save(item) }
        show(item)
        compose.onNodeWithTag(TAG_TYPE_PILL).performClick()
        compose.onNodeWithTag(TAG_TYPE_PILL).performClick()
        idle()
        assertEquals(ItemType.Goal, state.items.single().type)
        assertEquals(ItemType.Goal, runBlocking { repository.get("a")?.type })
    }

    @Test
    fun editingTheTitleOnTheCardSavesIt() {
        val item = Item("a", ItemType.Note, "cal sam")
        runBlocking { repository.save(item) }
        show(item)
        // The card's copy of the title comes after the carousel's.
        compose.onAllNodesWithText("cal sam").onLast().performTouchInput { longClick() }
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTextClearance()
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTextInput("Call Sam")
        compose.onNodeWithTag(TAG_TITLE_FIELD).performImeAction()
        idle()
        compose.onAllNodesWithText("Call Sam").onFirst().assertIsDisplayed()
        assertEquals("Call Sam", runBlocking { repository.get("a")?.title })
    }

    @Test
    fun tappingTheTitleOnTheCardEditsIt() {
        val item = Item("a", ItemType.Note, "cal sam")
        runBlocking { repository.save(item) }
        show(item)
        compose.onAllNodesWithText("cal sam").onLast().performTouchInput { click() }
        idle()
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTextClearance()
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTextInput("Call Sam")
        compose.onNodeWithTag(TAG_TITLE_FIELD).performImeAction()
        idle()
        compose.onAllNodesWithText("Call Sam").onFirst().assertIsDisplayed()
        assertEquals("Call Sam", runBlocking { repository.get("a")?.title })
    }

    @Test
    fun draggingFromTheTitleDoesNotEditIt() {
        val item = Item("a", ItemType.Note, "cal sam")
        runBlocking { repository.save(item) }
        show(item)
        compose.onAllNodesWithText("cal sam").onLast().performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            up()
        }
        idle()
        compose.onAllNodesWithTag(TAG_TITLE_FIELD).assertCountEquals(0)
    }

    private fun swipeCentre(toRight: Boolean) {
        compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
            val (from, to) = if (toRight) 0.2f to 0.8f else 0.8f to 0.2f
            down(Offset(width * from, height * 0.5f))
            moveTo(Offset(width * (from + to) / 2, height * 0.5f))
            moveTo(Offset(width * to, height * 0.5f))
            up()
        }
        idle()
    }

    @Test
    fun swipingRightHoldsTheCardAndSwipingLeftDropsItAtTheFocalCard() {
        show(Item("a", ItemType.Task, "A"), Item("b", ItemType.Task, "B"), Item("c", ItemType.Task, "C"))
        swipeCentre(toRight = true)
        assertEquals("a", state.held)
        state.moveTarget(2)
        swipeCentre(toRight = false)
        assertEquals(null, state.held)
        assertEquals(listOf("b", "c", "a"), state.items.map { it.id })
    }

    @Test
    fun drawingACheckWhileNothingIsHeldStillCompletes() {
        show(Item("a", ItemType.Task, "A"), Item("b", ItemType.Task, "B"))
        drawCheckmark()
        assertEquals(ItemStatus.Completed, statusOf("a"))
        assertEquals(null, state.held)
    }

    @Test
    fun groundIsLighterThanBeforeInBothModes() {
        assertTrue(overlayGroundAlpha(blurred = true) < 0.55f)
        assertTrue(overlayGroundAlpha(blurred = false) < 0.97f)
        assertTrue(overlayGroundAlpha(blurred = true) < overlayGroundAlpha(blurred = false))
    }
}
