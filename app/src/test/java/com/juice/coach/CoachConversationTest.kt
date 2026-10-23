package com.juice.coach

import com.juice.core.character.Characters
import com.juice.core.character.CoachTask
import com.juice.core.character.Expression
import com.juice.core.intake.ItemType
import com.juice.core.model.Item
import com.juice.core.routine.Cadence
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
    ) = CoachConversation(Characters.Jim, client, store, { onDeviceOnly }, { tasks })

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

    @Test fun checkInRequestIncludesEarlierHistoryAndCurrentCoachTasks() {
        reply("Let's start")
        conversation().send("I will walk on Mondays")
        server.takeRequest()
        reply("Welcome back")
        val fresh = ConversationStore(storeFile)
        CoachConversation(Characters.Jim, CoachClient("key-1", server.url("/").toString().trimEnd('/')), fresh, { false }, { listOf(walk) })
            .send("how am I doing")
        val body = server.takeRequest().body.readUtf8()
        assertTrue("I will walk on Mondays" in body)
        assertTrue("Walk 20 minutes" in body && "t1" in body)
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
}
