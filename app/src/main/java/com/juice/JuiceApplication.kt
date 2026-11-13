package com.juice

import android.app.Application
import com.juice.intake.IntakeDependencies

/** Holds the one [AppContainer] the whole process shares. */
open class JuiceApplication : Application(), IntakeDependencies.Provider {
    open val container: AppContainer by lazy { AppContainer(this) }

    override val intakeDependencies: IntakeDependencies get() = container
}
