package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OverlayShellTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private lateinit var state: OverlayShellState

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

    private fun show(vararg items: Item) {
        state = OverlayShellState(repository, scope, items.toList())
        // The clock only moves when a test moves it, so the 10 second undo window holds until then.
        compose.mainClock.autoAdvance = false
        compose.setContent { OverlayShell(state) }
        idle()
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
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertTextEquals("Enter new note or task…", includeEditableText = false)
    }

    @Test
    fun doneLeavesWritingModeAndKeepsDraft() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("draft")
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertDoesNotExist()
        compose.onNodeWithTag(TAG_WRITING_DONE).performClick()
        idle()
        compose.onNodeWithTag(TAG_CENTRE_CARD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).assertTextContains("draft")
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
}
