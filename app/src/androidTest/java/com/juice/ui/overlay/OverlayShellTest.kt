package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class OverlayShellTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private lateinit var state: OverlayShellState

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private fun show(vararg items: Item) {
        state = OverlayShellState(repository, CoroutineScope(Dispatchers.Main), items.toList())
        compose.setContent { OverlayShell(state) }
    }

    private fun statusOf(id: String) = runBlocking { repository.get(id)?.status }

    private fun drawCheckmark() = compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
        down(Offset(width * 0.2f, height * 0.5f))
        moveTo(Offset(width * 0.4f, height * 0.7f))
        moveTo(Offset(width * 0.8f, height * 0.3f))
        up()
    }

    private fun drawCross() = compose.onNodeWithTag(TAG_CENTRE_CARD).performTouchInput {
        down(Offset(width * 0.2f, height * 0.2f))
        moveTo(Offset(width * 0.8f, height * 0.8f))
        moveTo(Offset(width * 0.8f, height * 0.2f))
        moveTo(Offset(width * 0.2f, height * 0.8f))
        up()
    }

    @Test
    fun capturedTextAppearsAsSavedNote() {
        show()
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performTextInput("Buy milk")
        compose.onNodeWithTag(TAG_CAPTURE_FIELD).performImeAction()
        compose.onNodeWithText("Buy milk").assertIsDisplayed()
        compose.waitForIdle()
        val saved = runBlocking { repository.active() }
        assertEquals(listOf("Buy milk"), saved.map { it.title })
        assertEquals(ItemType.Note, saved.single().type)
    }

    @Test
    fun checkmarkCompletesItemAndOffersUndo() {
        show(Item("a", ItemType.Task, "Call Sam"))
        drawCheckmark()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        assertEquals(ItemStatus.Completed, statusOf("a"))
    }

    @Test
    fun crossDismissesAndUndoRestores() {
        val item = Item("a", ItemType.Task, "Call Sam")
        runBlocking { repository.save(item) }
        show(item)
        drawCross()
        compose.waitForIdle()
        assertEquals(ItemStatus.Archived, statusOf("a"))
        compose.onNodeWithTag(TAG_UNDO).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Call Sam").assertIsDisplayed()
        assertEquals(ItemStatus.Active, statusOf("a"))
    }

    @Test
    fun undoDisappearsAfterTenSeconds() {
        val item = Item("a", ItemType.Task, "Call Sam")
        show(item)
        drawCross()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(OverlayShellState.UNDO_WINDOW_MILLIS + 100)
        compose.onNodeWithTag(TAG_UNDO).assertDoesNotExist()
    }

    @Test
    fun pickingATypeChangesTheCardAndSavesIt() {
        val item = Item("a", ItemType.Note, "Call Sam")
        runBlocking { repository.save(item) }
        show(item)
        compose.onNodeWithTag(TAG_TYPE_PREFIX + "Goal").performClick()
        compose.waitForIdle()
        assertEquals(ItemType.Goal, state.items.single().type)
        assertEquals(ItemType.Goal, runBlocking { repository.get("a")?.type })
    }

    @Test
    fun editingTheTitleOnTheCardSavesIt() {
        val item = Item("a", ItemType.Note, "cal sam")
        runBlocking { repository.save(item) }
        show(item)
        compose.onNodeWithText("cal sam").performClick()
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTextClearance()
        compose.onNodeWithTag(TAG_TITLE_FIELD).performTextInput("Call Sam")
        compose.onNodeWithTag(TAG_TITLE_FIELD).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("Call Sam").assertIsDisplayed()
        assertEquals("Call Sam", runBlocking { repository.get("a")?.title })
    }
}
