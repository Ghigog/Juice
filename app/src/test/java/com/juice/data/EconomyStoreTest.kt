package com.juice.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.economy.EconomyState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class EconomyStoreTest {
    private lateinit var store: EconomyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = EconomyStore(context.getSharedPreferences("economy-test", Context.MODE_PRIVATE))
    }

    @Test
    fun loadBeforeAnySaveIsNull() {
        assertNull(store.load())
    }

    @Test
    fun savedJuiceMeterAndTimestampRoundTrip() {
        store.save(EconomyState(juice = 42, meter = 0.75), nowMillis = 1_234_567L)

        val saved = store.load()

        assertEquals(42, saved?.state?.juice)
        assertEquals(0.75, saved?.state?.meter)
        assertEquals(1_234_567L, saved?.savedAtMillis)
    }
}
