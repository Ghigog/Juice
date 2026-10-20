package com.juice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OverlayShellTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: JuiceDatabase
    private lateinit var repo: ItemRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), JuiceDatabase::class.java).build()
        repo = ItemRepository(db.itemDao())
    }

    @After fun tearDown() = db.close()

    private fun show(vararg titles: String) {
        val items = titles.map { Item(it, ItemType.Task, it) }
        runBlocking { items.forEach { repo.save(it) } }
        rule.setContent { OverlayShell(repo, items) }
    }

    private fun TouchInjectionScope.path(vararg fractions: Pair<Float, Float>) {
        val pts = fractions.map { Offset(width * it.first, height * it.second) }
        down(pts.first())
        pts.drop(1).forEach { moveTo(it) }
        up()
    }

    private fun drawCheck(tag: String) = rule.onNodeWithTag(tag).performTouchInput {
        path(0.2f to 0.5f, 0.35f to 0.8f, 0.8f to 0.2f)
    }

    private fun drawCross(tag: String) = rule.onNodeWithTag(tag).performTouchInput {
        path(0.2f to 0.2f, 0.8f to 0.8f, 0.8f to 0.2f, 0.2f to 0.8f)
    }

    @Test fun captureShowsNoteAndSavesIt() {
        show()
        rule.onNodeWithTag("capture-field").performTextInput("Buy milk")
        rule.onNodeWithTag("capture-button").performClick()
        rule.onNodeWithText("Buy milk").assertIsDisplayed()
        val saved = runBlocking { repo.active() }
        assertEquals(listOf("Buy milk" to ItemType.Note), saved.map { it.title to it.type })
    }

    @Test fun checkmarkCompletesAndOffersUndo() {
        show("Call mum")
        drawCheck("card-Call mum")
        rule.onNodeWithTag("card-Call mum").assertDoesNotExist()
        rule.onNodeWithTag("undo").assertIsDisplayed()
        assertEquals(ItemStatus.Completed, runBlocking { repo.get("Call mum") }!!.status)
    }

    @Test fun crossDismissesAndUndoRestores() {
        show("Call mum")
        drawCross("card-Call mum")
        rule.onNodeWithTag("card-Call mum").assertDoesNotExist()
        rule.onNodeWithTag("undo").performClick()
        rule.onNodeWithTag("card-Call mum").assertIsDisplayed()
        assertEquals(ItemStatus.Active, runBlocking { repo.get("Call mum") }!!.status)
    }

    @Test fun undoDisappearsAfterTenSeconds() {
        show("Call mum")
        drawCheck("card-Call mum")
        rule.mainClock.advanceTimeBy(UNDO_WINDOW_MILLIS + 100)
        rule.onNodeWithTag("undo").assertDoesNotExist()
    }

    @Test fun unrecognisedScribbleKeepsItem() {
        show("Call mum")
        rule.onNodeWithTag("card-Call mum").performTouchInput { path(0.2f to 0.5f, 0.8f to 0.5f) }
        rule.onNodeWithTag("card-Call mum").assertIsDisplayed()
        rule.onNodeWithTag("undo").assertDoesNotExist()
    }
}
