package com.juice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.juice.core.category.Category
import com.juice.core.economy.CategoryEconomy
import com.juice.core.economy.EconomyState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class EconomyStoreTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var store: EconomyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("economy-test", Context.MODE_PRIVATE)
        store = EconomyStore(prefs)
    }

    @Test
    fun loadBeforeAnySaveIsNull() {
        assertNull(store.load())
    }

    @Test
    fun saveByThePreviousVersionLoadsAsMain() {
        prefs.edit().putLong("juice", 6_798).putFloat("meter", 5f).putLong("savedAt", 9L).apply()

        val saved = assertNotNull(store.load())

        assertEquals(EconomyState(6_798, 5.0), saved.economy[Category.MAIN_ID])
        assertEquals(setOf(Category.MAIN_ID), saved.economy.states.keys)
        assertEquals(6_798, saved.economy.totalJuice)
    }

    @Test
    fun nextSaveWritesTheNewFormatAndRemovesLegacyKeys() {
        prefs.edit().putLong("juice", 6_798).putFloat("meter", 5f).putLong("savedAt", 9L).apply()

        store.save(assertNotNull(store.load()).economy, 10L)

        assertFalse(prefs.contains("juice"))
        assertFalse(prefs.contains("meter"))
        assertEquals(EconomyState(6_798, 5.0), EconomyStore(prefs).load()?.economy?.get(Category.MAIN_ID))
    }

    @Test
    fun categoriesAndTimestampRoundTrip() {
        val economy = CategoryEconomy(
            states = mapOf(
                Category.MAIN_ID to EconomyState(100, 0.0),
                "game-ideas" to EconomyState(40, 12.0),
            ),
        )
        store.save(economy, nowMillis = 1_234_567L)

        val saved = assertNotNull(EconomyStore(prefs).load())

        assertEquals(economy.states, saved.economy.states)
        assertEquals(1_234_567L, saved.savedAtMillis)
    }
}
