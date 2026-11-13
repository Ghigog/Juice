package com.juice.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.GroqKey
import com.juice.intake.GroqClient
import com.juice.intake.KeyCheck
import com.juice.settings.GroqKeyStore
import com.juice.settings.KeyCipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private object PlainCipher : KeyCipher {
    override fun encrypt(plain: ByteArray) = plain
    override fun decrypt(sealed: ByteArray) = sealed
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroqKeyActionsTest {
    private val store = GroqKeyStore(ApplicationProvider.getApplicationContext<Context>(), PlainCipher)
    private val key = assertNotNull(GroqKey.parse("abcd_secret1234"))

    /** The fake client's server answers [code]; the built keys are recorded. */
    private fun TestScope.actions(server: MockWebServer, built: MutableList<GroqKey>) = GroqKeyActions(
        store,
        { built += it; GroqClient(it.value, server.url("/").toString().trimEnd('/')) },
        this,
        StandardTestDispatcher(testScheduler),
    )

    @Test fun savingChecksTheFakeClientShowsTheMaskAndCallsBack() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(200).setBody("{}")); start() }
        val built = mutableListOf<GroqKey>()
        val actions = actions(server, built)
        var saved = 0
        var checked: KeyCheck? = null
        actions.save(key, onSaved = { saved++ }, onChecked = { checked = it })
        testScheduler.advanceUntilIdle()
        // The check runs on a real thread pool only when the dispatcher is Dispatchers.IO; here it ran on the test one.
        assertEquals(KeyCheck.Works, checked)
        assertEquals(1, saved)
        assertEquals(listOf(key), built)
        assertEquals(key.masked, actions.masked.value)
        assertEquals(key.value, store.load()?.value)
        server.shutdown()
    }

    @Test fun removingForgetsTheKeyAndItsMask() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(200).setBody("{}")); start() }
        val actions = actions(server, mutableListOf())
        actions.save(key, {}, {})
        testScheduler.advanceUntilIdle()
        actions.remove()
        assertNull(actions.masked.value)
        assertNull(store.load())
        server.shutdown()
    }

    @Test fun loadMaskedShowsTheStoredKey() = runTest {
        store.save(key)
        val actions = GroqKeyActions(store, { error("no client needed") }, this, StandardTestDispatcher(testScheduler))
        actions.loadMasked()
        testScheduler.advanceUntilIdle()
        assertEquals(key.masked, actions.masked.value)
    }
}
