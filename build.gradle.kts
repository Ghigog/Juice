plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":core"))
    kover(project(":app"))
}

kover {
    reports {
        verify {
            rule {
                // Merged :app and :core line coverage; measured 79.0% when set.
                minBound(79)
            }
        }
    }
}
