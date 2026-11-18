package com.juice

import android.app.Application
import com.juice.intake.IntakeDependencies
import java.time.Clock

/** Holds the one [AppContainer] the whole process shares. */
open class JuiceApplication : Application(), IntakeDependencies.Provider {
    open val container: AppContainer by lazy { AppContainer(this, Clock.systemDefaultZone()) }

    override val intakeDependencies: IntakeDependencies get() = container
}
