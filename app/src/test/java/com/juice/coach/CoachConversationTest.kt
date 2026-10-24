package com.juice.coach

import com.juice.core.character.Characters
import com.juice.core.character.CoachTask
import com.juice.core.character.Expression
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.routine.Cadence
import com.juice.core.memory.Memory
import com.juice.memory.MemoryStore
import java.io.File
import java.time.DayOfWeek
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class CoachConversationTest {
    private val server = MockWebServer().apply { start() }
    private val dir = File(System.getProperty("java.io.tmpdir"), "coach-test-${System.nanoTime()}").apply { mkdirs() }
    private val storeFile = File(dir, "conversations.json")
    private val store = ConversationStore(storeFile)
    private val memoryStore = MemoryStore(File(dir, "memory.json"))
    private val walk = Item("t1", ItemType.Routine, "Walk 20 minutes", cadence = Cadence(setOf(DayOfWeek.MONDAY)))

    @AfterTest fun stop() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun reply(content: String, status: Int = 200) {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        server.enqueue(MockResponse().setResponseCode(status).setBody("""{"choices":[{"message":{"content":"$escaped"}}]}"""))
    }

    private fun conversation(
        onDeviceOnly: Boolean = false,
        client: CoachClient? = CoachClient("key-1", server.url("/").toString().trimEnd('/')),
        tasks: List<Item> = emptyList(),
    ) = CoachConversation(Characters.Jim, client, store, memoryStore, { onDeviceOnly }, { tasks })

    @Test fun sendPostsPersonaAndMessagesWithoutJsonMode() {
        reply("Hi, what do you do now?")
        val result = conversation().send("I want to get fit")
        assertEquals(CoachReply.Reply("Hi, what do you do now?"), result)
        val request = server.takeRequest()
        assertEquals("Bearer key-1", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue("Coach Humble Jumble Jim" in body)
        assertTrue("I want to get fit" in body)
        assertTrue("json_object" !in body)
    }

    @Test fun onDeviceOnlyOrNoKeyIsCloudRequiredWithoutARequest() {
        assertEquals(CoachReply.CloudRequired, conversation(onDeviceOnly = true).send("hi"))
        assertEquals(CoachFinish.CloudRequired, conversation(onDeviceOnly = true).finish())
        assertEquals(CoachReply.CloudRequired, conversation(client = null).send("hi"))
        assertEquals(0, server.requestCount)
    }

    @Test fun unreachableGroqIsAnErrorAndHistoryIsUnchanged() {
        reply("ok")
        val chat = conversation()
        chat.send("first")
        reply("", status = 503)
        assertTrue(chat.send("second") is CoachReply.Error)
        assertEquals(listOf(ChatMessage("user", "first"), ChatMessage("assistant", "ok")), store.load(Characters.JIM_ID))
        reply("fine")
        chat.send("third")
        server.takeRequest(); server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue("second" !in body)
    }

    @Test fun finishUsesJsonModeAndReturnsValidatedTasks() {
        reply("""{"tasks":[{"action":"add","title":"Walk","cadence":{"days":"daily"}}]}""")
        val result = conversation().finish()
        assertTrue(result is CoachFinish.Proposal)
        assertEquals(CoachTask.Add("Walk", Cadence(DayOfWeek.entries.toSet())), result.tasks.single())
        assertTrue("json_object" in server.takeRequest().body.readUtf8())
    }

    @Test fun malformedOrInvalidProposalReturnsNoTasks() {
        reply("not json")
        assertTrue(conversation().finish() is CoachFinish.Rejected)
        reply("""{"tasks":[{"action":"remove","id":"nope"}]}""")
        assertTrue(conversation(tasks = listOf(walk)).finish() is CoachFinish.Rejected)
    }

    @Test fun clearForgetsOnlyThatCharactersConversation() {
        store.save("jim", listOf(ChatMessage("user", "hi")))
        store.save("other", listOf(ChatMessage("user", "yo")))
        store.clear("jim")
        assertEquals(emptyList(), ConversationStore(storeFile).load("jim"))
        assertEquals(listOf(ChatMessage("user", "yo")), ConversationStore(storeFile).load("other"))
    }

    private val validProposal = """{"tasks":[]}"""
    private val url get() = server.url("/").toString().trimEnd('/')

    @Test fun checkInPromptIncludesCurrentCoachTasksButNotTheRawTranscript() {
        store.save("jim", listOf(ChatMessage("user", "I will walk on Mondays")))
        reply("Welcome back")
        conversation(tasks = listOf(walk)).send("how am I doing")
        val body = server.takeRequest().body.readUtf8()
        assertTrue("I will walk on Mondays" !in body)
        assertTrue("Walk 20 minutes" in body && "t1" in body)
    }

    @Test fun aFactFromAFinishedConversationIsInALaterPromptForAnyCharacter() {
        reply("Nice")
        val chat = conversation()
        chat.send("I am vegetarian")
        reply(validProposal)
        reply("""{"shared":["Player is vegetarian"],"characters":{}}""")
        assertTrue(chat.finish() is CoachFinish.Proposal)
        repeat(3) { server.takeRequest() }
        reply("Hello")
        CoachConversation(Characters.Nutritionist, CoachClient("key-1", url), store, memoryStore, { false }, { emptyList() }).send("hi")
        assertTrue("Player is vegetarian" in server.takeRequest().body.readUtf8())
    }

    @Test fun aCharactersNoteIsAbsentFromAnotherCharactersPrompt() {
        memoryStore.save(Memory(characters = mapOf("jim" to listOf("Jim-only secret"))))
        reply("Hello")
        CoachConversation(Characters.Nutritionist, CoachClient("key-1", url), store, memoryStore, { false }, { emptyList() }).send("hi")
        assertTrue("Jim-only secret" !in server.takeRequest().body.readUtf8())
        reply("Hello")
        conversation().send("hi")
        assertTrue("Jim-only secret" in server.takeRequest().body.readUtf8())
    }

    @Test fun invalidOrFailingMemoryUpdateLeavesNotesAndProposalAlone() {
        val notes = Memory(shared = listOf("old"))
        memoryStore.save(notes)
        reply(validProposal)
        reply("not json")
        assertTrue(conversation().finish() is CoachFinish.Proposal)
        assertEquals(notes, memoryStore.load())
        reply(validProposal)
        reply("", status = 503)
        assertTrue(conversation().finish() is CoachFinish.Proposal)
        assertEquals(notes, memoryStore.load())
    }

    @Test fun onDeviceOnlySendsNoMemoryCall() {
        val notes = Memory(shared = listOf("old"))
        memoryStore.save(notes)
        assertEquals(CoachFinish.CloudRequired, conversation(onDeviceOnly = true).finish())
        assertEquals(0, server.requestCount)
        assertEquals(notes, memoryStore.load())
    }

    @Test fun taggedReplyIsStrippedAndSavedWithItsExpression() {
        reply("[sad] Tough week")
        assertEquals(CoachReply.Reply("Tough week", Expression.SAD), conversation().send("hi"))
        assertEquals(
            listOf(ChatMessage("user", "hi"), ChatMessage("assistant", "Tough week", Expression.SAD)),
            ConversationStore(storeFile).load(Characters.JIM_ID),
        )
    }

    @Test fun untaggedReplyIsNeutralWithFullText() {
        reply("Plain answer")
        assertEquals(CoachReply.Reply("Plain answer", Expression.NEUTRAL), conversation().send("hi"))
    }

    @Test fun legacyHistoryWithoutExpressionLoadsAsNeutral() {
        storeFile.writeText("""{"jim":[{"role":"user","content":"hi"},{"role":"assistant","content":"yo","expression":"bogus"}]}""")
        assertEquals(Expression.NEUTRAL, conversation().lastExpression)
        assertEquals(ChatMessage("assistant", "yo"), store.load("jim").last())
    }

    @Test fun lastExpressionIsTheLastCoachMessagesSavedExpression() {
        store.save("jim", listOf(ChatMessage("assistant", "grr", Expression.ANGRY), ChatMessage("user", "sorry")))
        assertEquals(Expression.ANGRY, conversation().lastExpression)
    }

    @Test fun storeSaveFailureIsAnErrorNotAThrow() {
        storeFile.delete()
        storeFile.mkdirs()
        File(storeFile, "blocker").writeText("x")
        reply("Hello")
        assertTrue(conversation().send("hi") is CoachReply.Error)
    }

    @Test fun nonIoClientFailureIsAnError() {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertTrue(conversation().send("hi") is CoachReply.Error)
    }

    @Test fun readyReplyWithValidProposalIsAProposalWithoutTheTag() {
        reply("[happy] Let's do it [ready]")
        reply("""{"tasks":[{"action":"add","title":"Walk","cadence":{"days":"daily"}}]}""")
        reply("""{"shared":[],"characters":{}}""")
        val result = conversation().send("walking daily")
        assertEquals(
            CoachReply.Proposal("Let's do it", Expression.HAPPY, listOf(CoachTask.Add("Walk", Cadence(DayOfWeek.entries.toSet())))),
            result,
        )
        assertEquals("Let's do it", store.load(Characters.JIM_ID).first { it.role == "assistant" }.content)
    }

    @Test fun readyReplyWithInvalidProposalIsRejectedAndChatContinues() {
        reply("Ready [ready]")
        reply("not json")
        val chat = conversation()
        val result = chat.send("hi")
        assertTrue(result is CoachReply.Rejected)
        assertEquals("Ready", result.text)
        reply("Go on")
        assertEquals(CoachReply.Reply("Go on"), chat.send("more"))
    }
}
