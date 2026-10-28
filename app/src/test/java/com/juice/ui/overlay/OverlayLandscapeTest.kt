package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.JuiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The overlay on a phone turned on its side. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class OverlayLandscapeTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private lateinit var db: JuiceDatabase
    private lateinit var state: OverlayShellState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show(vararg items: Item) {
        state = OverlayShellState(ItemRepository(db.itemDao()), scope, items.toList())
        compose.mainClock.autoAdvance = false
        compose.setContent { OverlayShell(state, settings = JuiceSettings(reduceMotion = true, tapButtons = true)) }
        idle()
    }

    private fun idle() {
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun theTitleAndTheButtonsFitOnTheCard() {
        show(Item("a", ItemType.Goal, "Drink water every two hours"))
        val cardTitle = hasText("Drink water every two hours") and hasAnyAncestor(hasTestTag(TAG_CENTRE_CARD))
        compose.onNode(cardTitle, useUnmergedTree = true).assertIsDisplayed()
        val card = bounds(TAG_CENTRE_CARD)
        val complete = bounds(TAG_COMPLETE)
        assertTrue("Complete $complete sits inside the card $card", complete.bottom <= card.bottom && complete.top >= card.top)
        // Beside the title rather than under it.
        val title = compose.onNode(cardTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Complete $complete is right of the title $title", complete.left >= title.right)
    }

    @Test
    fun theUndoPromptSitsInTheHeaderNotOverTheCard() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_COMPLETE).performClick()
        idle()
        compose.onNodeWithTag(TAG_UNDO).assertIsDisplayed()
        assertTrue(bounds(TAG_UNDO).bottom <= bounds(TAG_CENTRE_CARD).top)
    }

    @Test
    fun theSettingsSheetStaysPhoneWide() {
        show(Item("a", ItemType.Task, "Call Sam"))
        compose.onNodeWithTag(TAG_SETTINGS).performClick()
        idle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag(TAG_SETTINGS_DONE).fetchSemanticsNode().boundsInRoot
        // A screen-wide sheet would put Done about 20dp from the right edge.
        assertTrue("Done $sheet on a screen $root", sheet.right < root.right * 0.9f)
    }
}
