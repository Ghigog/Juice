package com.juice.ui.overlay

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.juice.RegisterComposeActivity
import com.juice.coach.CoachClient
import com.juice.coach.CoachConversation
import com.juice.coach.ConversationStore
import com.juice.memory.MemoryStore
import com.juice.core.character.Characters
import com.juice.coach.ChatMessage
import com.juice.core.character.Expression
import com.juice.feedback.Feedback
import com.juice.feedback.FeedbackEvent
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.model.ItemStatus
import com.juice.core.routine.Cadence
import com.juice.data.CharacterRepository
import com.juice.data.CharacterStore
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CoachChatTest {
    val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeActivity()).around(compose)

    private val jim = Characters.Jim
    private val server = MockWebServer().apply { start() }
    private val dir = File(System.getProperty("java.io.tmpdir"), "coach-chat-${System.nanoTime()}").apply { mkdirs() }
    private lateinit var db: JuiceDatabase
    private lateinit var repository: ItemRepository
    private lateinit var characters: CharacterRepository
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
        repository = ItemRepository(db.itemDao())
        characters = CharacterRepository(CharacterStore(context.getSharedPreferences("chat-test", Context.MODE_PRIVATE)), repository)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        server.shutdown()
        dir.deleteRecursively()
    }

    private val meet = Item("meet-jim", ItemType.Task, "Meet Coach Humble Jumble Jim", characterId = "jim")
    private val checkIn = Item("checkin-jim", ItemType.Task, "Check in with Coach Humble Jumble Jim", characterId = "jim")

    private fun reply(content: String) {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"$escaped"}}]}"""))
    }

    private fun show(card: Item, onDeviceOnly: Boolean = false) {
        runBlocking { repository.save(card) }
        val coachTasks = { runBlocking { repository.byCharacter("jim").filter { it.type == ItemType.Routine } } }
        state = OverlayShellState(
            repository, scope, listOf(card),
            characters = characters,
            coachChatFor = { id, onFailed ->
                val conversation = CoachConversation(
                    jim, CoachClient("key", server.url("/").toString().trimEnd('/')),
                    ConversationStore(File(dir, "conversations.json")), MemoryStore(File(dir, "memory.json")), { onDeviceOnly }, coachTasks,
                )
                CoachChatState(jim, conversation, characters, scope, coachTasks, onFailed)
            },
        )
        val effects = OverlayEffects()
        compose.setContent { OverlayShell(state, effects = effects) }
        compose.waitForIdle()
        compose.runOnIdle { state.complete(card) }
    }

    /** Waits for [done], letting the main looper run between looks so launched work gets its turn. */
    private fun waitFor(done: () -> Boolean) = compose.waitUntil(5_000) { compose.waitForIdle(); done() }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun routines() = runBlocking { repository.byCharacter("jim").filter { it.type == ItemType.Routine } }

    private fun propose(vararg tasks: String) {
        reply("""{"tasks":[${tasks.joinToString(",")}]}""")
        reply("""{"shared":[],"characters":{}}""")
        compose.onNodeWithTag(TAG_COACH_FINISH).performClick()
        waitForText("Proposed regular tasks")
    }

    private fun add(title: String) = """{"action":"add","title":"$title","cadence":{"days":"daily"}}"""

    @Test
    fun completingTheMeetCardOpensTheChatWithTheCoachsOpeningLine() {
        show(meet)
        compose.onNodeWithTag(TAG_COACH_CHAT).assertIsDisplayed()
        waitForText(jim.openingLine)
    }

    @Test
    fun confirmingTheProposalCreatesRoutineCardsForTheCoachAndRecordsTheConversation() {
        show(meet)
        compose.onNodeWithTag(TAG_COACH_INPUT).performTextInput("I walk a little")
        reply("Sounds good")
        compose.onNodeWithTag(TAG_COACH_SEND).performClick()
        waitForText("Sounds good")
        propose(add("Walk 20 minutes"), add("Stretch"), add("Drink water"))
        compose.onNodeWithTag(TAG_COACH_CONFIRM).performClick()
        waitFor { routines().size == 3 }
        assertEquals(setOf("Walk 20 minutes", "Stretch", "Drink water"), routines().map { it.title }.toSet())
        assertEquals(true, routines().all { it.characterId == "jim" })
        assertNotNull(characters.state("jim").lastConversationAt)
        assertEquals(0, compose.onAllNodesWithTagCount(TAG_COACH_CHAT))
    }

    @Test
    fun cancellingTheProposalCreatesNothing() {
        show(meet)
        propose(add("Walk 20 minutes"))
        compose.onNodeWithTag(TAG_COACH_CANCEL).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<Item>(), routines())
        assertNull(characters.state("jim").lastConversationAt)
        assertEquals(0, compose.onAllNodesWithTagCount(TAG_COACH_CHAT))
    }

    @Test
    fun onDeviceOnlySendsNothingTellsThePlayerAndKeepsTheCard() {
        show(meet, onDeviceOnly = true)
        waitForText("needs cloud access")
        waitFor { compose.onAllNodesWithTagCount(TAG_COACH_CHAT) == 0 }
        assertEquals(0, server.requestCount)
        assertEquals(ItemStatus.Active, runBlocking { repository.get("meet-jim")?.status })
        assertEquals(listOf("meet-jim"), state.items.map { it.id })
    }

    @Test
    fun anUnreachableCoachClosesTheChatAndKeepsTheCheckInCard() {
        show(checkIn)
        server.enqueue(MockResponse().setResponseCode(500))
        compose.onNodeWithTag(TAG_COACH_FINISH).performClick()
        waitForText("could not be reached")
        assertEquals(ItemStatus.Active, runBlocking { repository.get("checkin-jim")?.status })
        assertEquals(listOf("checkin-jim"), state.items.map { it.id })
    }

    @Test
    fun aCheckInProposalRemovingOneTaskAndAddingAnotherUpdatesTheCoachsTasks() {
        val walk = Item("t1", ItemType.Routine, "Walk 20 minutes", characterId = "jim", cadence = Cadence(setOf(DayOfWeek.MONDAY)))
        val swim = Item("t2", ItemType.Routine, "Swim", characterId = "jim", cadence = Cadence(setOf(DayOfWeek.FRIDAY)))
        runBlocking { repository.save(walk); repository.save(swim) }
        show(checkIn)
        propose("""{"action":"remove","id":"t2"}""", add("Yoga"))
        waitForText("Remove: Swim")
        compose.onNodeWithTag(TAG_COACH_CONFIRM).performClick()
        waitFor { routines().map { it.title }.toSet() == setOf("Walk 20 minutes", "Yoga") }
        assertNull(runBlocking { repository.get("t2") })
    }

    private fun chat(onFailed: () -> Unit): CoachChatState {
        val conversation = CoachConversation(
            jim, CoachClient("key", server.url("/").toString().trimEnd('/')),
            ConversationStore(File(dir, "conversations.json")), MemoryStore(File(dir, "memory.json")), { false }, { emptyList() },
        )
        return CoachChatState(jim, conversation, characters, scope, onFailed = onFailed)
    }

    private fun sendAndWait(chat: CoachChatState, content: String, text: String = "hi") {
        reply(content)
        chat.send(text)
        waitFor { !chat.busy }
    }

    @Test
    fun aTaggedReplySetsTheExpressionAndLosesTheTag() {
        val chat = chat {}
        assertEquals(Expression.NEUTRAL, chat.expression)
        sendAndWait(chat, "[happy] Nice work")
        assertEquals(Expression.HAPPY, chat.expression)
        assertEquals("Nice work", chat.messages.last().content)
        assertEquals(Expression.HAPPY, chat.messages.last().expression)
    }

    @Test
    fun anUnknownTagFallsBackToNeutralAndKeepsTheFullText() {
        val chat = chat {}
        sendAndWait(chat, "[happy] Nice")
        sendAndWait(chat, "[confused] Huh")
        assertEquals(Expression.NEUTRAL, chat.expression)
        assertEquals("[confused] Huh", chat.messages.last().content)
    }

    @Test
    fun anErrorLeavesTheExpressionUnchanged() {
        val chat = chat {}
        sendAndWait(chat, "[happy] Nice")
        server.enqueue(MockResponse().setResponseCode(500))
        chat.send("again")
        waitFor { !chat.busy }
        assertEquals(Expression.HAPPY, chat.expression)
    }

    @Test
    fun aReopenedChatStartsWithTheSavedExpression() {
        ConversationStore(File(dir, "conversations.json")).save(
            jim.id, listOf(ChatMessage("user", "hi"), ChatMessage("assistant", "Grr", Expression.ANGRY)),
        )
        assertEquals(Expression.ANGRY, chat {}.expression)
    }

    @Test
    fun theProposalLeavesTheExpressionUnchanged() {
        val chat = chat {}
        sendAndWait(chat, "[happy] Nice")
        reply("""{"tasks":[${add("Walk")}]}""")
        reply("""{"shared":[],"characters":{}}""")
        chat.finish()
        waitFor { chat.proposal != null }
        assertEquals(Expression.HAPPY, chat.expression)
    }

    @Test
    fun theHeaderShowsTheCoachsFaceAndName() {
        val chat = chat {}
        compose.setContent { CoachChat(chat, Hud()) }
        compose.onNodeWithTag(TAG_COACH_FACE).assertIsDisplayed()
        waitForText(jim.name)
    }

    @Test
    fun theNutritionistsChatOpensWithHerOwnLine() {
        val nora = Characters.Nutritionist
        val conversation = CoachConversation(
            nora, CoachClient("key", server.url("/").toString().trimEnd('/')),
            ConversationStore(File(dir, "conversations.json")), MemoryStore(File(dir, "memory.json")), { false }, { emptyList() },
        )
        val chat = CoachChatState(nora, conversation, characters, scope)
        compose.setContent { CoachChat(chat, Hud()) }
        waitForText(nora.openingLine)
    }

    @Test
    fun aNewReplyTypesOutWithAMutterPerLetterAtTheCharactersPitch() {
        val rates = mutableListOf<Float>()
        val feedback = object : Feedback {
            override fun play(event: FeedbackEvent, boosted: Boolean) {}
            override fun mutter(rate: Float) { rates += rate }
        }
        val conversation = CoachConversation(
            jim, CoachClient("key", server.url("/").toString().trimEnd('/')),
            ConversationStore(File(dir, "conversations.json")), MemoryStore(File(dir, "memory.json")), { false }, { emptyList() },
        )
        val chat = CoachChatState(jim, conversation, characters, scope, feedback = feedback)
        compose.setContent { CoachChat(chat, Hud()) }
        sendAndWait(chat, "Hey")
        waitForText("Hey")
        waitFor { rates.size == 3 }
        assertEquals(listOf(jim.mutterPitch, jim.mutterPitch, jim.mutterPitch), rates)
    }

    @Test
    fun cancellingCallsOnFailedWithoutANotice() {
        var failed = 0
        val chat = chat { failed++ }
        chat.cancel()
        assertEquals(1, failed)
        assertEquals(false, chat.open)
        assertNull(chat.notice)
    }

    @Test
    fun confirmingDoesNotCallOnFailed() {
        var failed = 0
        show(meet)
        propose(add("Walk 20 minutes"))
        val chat = chat { failed++ }
        chat.confirm()
        assertEquals(0, failed)
    }

    @Test
    fun cancellingAfterAFailureCallsOnFailedOnlyOnce() {
        var failed = 0
        val chat = chat { failed++ }
        chat.start()
        server.enqueue(MockResponse().setResponseCode(500))
        chat.finish()
        waitFor { !chat.open }
        chat.cancel()
        assertEquals(1, failed)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
