package com.juice

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import com.juice.overlay.OverlayService

@RunWith(RobolectricTestRunner::class)
class JuiceApplicationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>() as JuiceApplication

    @Test
    fun containerHandsOutTheSameRepositoriesEveryTime() {
        val container = app.container
        assertSame(container.items, app.container.items)
        assertSame(container.characters, app.container.characters)
        assertSame(container, app.container)
    }

    @Test
    fun serviceAndActivityShareTheApplicationsContainer() {
        val service = Robolectric.buildService(OverlayService::class.java).get()
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        assertSame(app.container, (service.applicationContext as JuiceApplication).container)
        assertSame(app.container.items, (activity.applicationContext as JuiceApplication).container.items)
    }
}
