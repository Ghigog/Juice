package com.juice.intake

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.juice.core.intake.ItemType
import com.juice.core.intake.RulesFallback
import com.juice.core.model.Item
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.SettingsRepository
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntakeWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer().apply { start() }
    private lateinit var db: JuiceDatabase
    private lateinit var items: ItemRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, JuiceDatabase::class.java).allowMainThreadQueries().build()
        items = ItemRepository(db.itemDao())
        runBlocking { items.save(Item("i1", ItemType.Note, "raw")) }
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun worker(vararg input: Pair<String, Any?>) =
        TestListenableWorkerBuilder<IntakeWorker>(context)
            .setInputData(workDataOf(*input))
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    IntakeWorker(
                        appContext,
                        workerParameters,
                        SettingsRepository(appContext),
                        GroqClient("key", server.url("/").toString().trimEnd('/')),
                        items,
                    )
            })
            .build()

    @Test
    fun missingTextFails() = runBlocking {
        assertEquals(Result.failure(), worker("itemId" to "i1").doWork())
    }

    @Test
    fun missingItemIdFails() = runBlocking {
        assertEquals(Result.failure(), worker("text" to "do laundry").doWork())
    }

    @Test
    fun aValidGroqParseLandsTheItemAndSucceeds() = runBlocking {
        val deadline = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val json = """{\"type\":\"Task\",\"title\":\"Do laundry\",\"completion_condition\":null,\"deadline\":\"$deadline\",\"sub_items\":null}"""
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"$json"}}]}"""))
        assertEquals(Result.success(), worker("text" to "do laundry", "itemId" to "i1").doWork())
        val landed = items.get("i1")!!
        assertEquals(deadline, landed.deadline)
    }

    @Test
    fun aGroqTransportErrorLandsTheRulesResultAndRetries() = runBlocking {
        server.shutdown()
        val text = "do laundry"
        assertEquals(Result.retry(), worker("text" to text, "itemId" to "i1").doWork())
        val rules = RulesFallback.classify(text, ZonedDateTime.now())
        val landed = items.get("i1")!!
        assertEquals(rules.type, landed.type)
        assertEquals(rules.deadline, landed.deadline)
        assertNull(landed.deadline)
    }
}
