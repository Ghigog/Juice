package com.juice.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.card.CardOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun updateIsVisibleInCurrent() = runTest {
        repository.update { it.copy(sound = false, swipeDistance = SwipeDistance.Long, handleX = 0.25f) }

        val current = repository.current()

        assertEquals(false, current.sound)
        assertEquals(SwipeDistance.Long, current.swipeDistance)
        assertEquals(0.25f, current.handleX)
    }

    @Test
    fun onDeviceOnlyDefaultsToTrueWhenNothingIsStored() = runTest {
        assertTrue(repository.current().onDeviceOnly)
    }

    @Test
    fun cardOrderDefaultsToNewestAndRoundTrips() = runTest {
        assertEquals(CardOrder.Newest, repository.current().cardOrder)

        repository.update { it.copy(cardOrder = CardOrder.Oldest) }

        assertEquals(CardOrder.Oldest, repository.current().cardOrder)
    }

    @Test
    fun markOnboardingSeenShowsOnboardingSeen() = runTest {
        repository.markOnboardingSeen()

        assertTrue(repository.onboardingSeen.first())
    }
}
