package com.juice.intake

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.GroqKey
import com.juice.settings.GroqKeyStore
import com.juice.settings.KeyCipher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private object PlainCipher : KeyCipher {
    override fun encrypt(plain: ByteArray) = plain
    override fun decrypt(sealed: ByteArray) = sealed
}

@RunWith(RobolectricTestRunner::class)
class GroqKeyCheckTest {
    private val server = MockWebServer().apply { start() }
    private val store = GroqKeyStore(ApplicationProvider.getApplicationContext<Context>(), PlainCipher)
    private val key = assertNotNull(GroqKey.parse("abcd_secret1234"))
    private val built = mutableListOf<GroqKey>()
    private val groqFor: (GroqKey) -> GroqClient = { built += it; GroqClient(it.value, server.url("/").toString().trimEnd('/')) }

    @AfterTest fun stop() = server.shutdown()

    private fun check(response: MockResponse): KeyCheck {
        server.enqueue(response)
        return saveAndCheckGroqKey(key, store, groqFor)
    }

    @Test fun savesTheKeyAndAsksTheSuppliedClientOnce() {
        assertEquals(KeyCheck.Works, check(MockResponse().setResponseCode(200).setBody("{}")))
        assertEquals(key.value, store.load()?.value)
        assertEquals(listOf(key), built)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/models", request.path)
        assertEquals("Bearer abcd_secret1234", request.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectedWhenGroqAnswers401() = assertEquals(KeyCheck.Rejected, check(MockResponse().setResponseCode(401)))

    @Test fun unreachableWhenGroqAnswers500() = assertEquals(KeyCheck.Unreachable, check(MockResponse().setResponseCode(500)))

    @Test fun unreachableWhenTheFactoryThrows() {
        assertEquals(KeyCheck.Unreachable, saveAndCheckGroqKey(key, store) { error("boom") })
    }
}
