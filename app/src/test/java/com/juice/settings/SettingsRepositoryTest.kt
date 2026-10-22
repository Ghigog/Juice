package com.juice.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun updateIsVisibleInCurrent() = runBlocking {
        repository.update { it.copy(sound = false, swipeDistance = SwipeDistance.Long, handleX = 0.25f) }

        val current = repository.current()

        assertEquals(false, current.sound)
        assertEquals(SwipeDistance.Long, current.swipeDistance)
        assertEquals(0.25f, current.handleX)
    }

    @Test
    fun markOnboardingSeenShowsOnboardingSeen() = runBlocking {
        repository.markOnboardingSeen()

        assertTrue(repository.onboardingSeen.first())
    }
}
