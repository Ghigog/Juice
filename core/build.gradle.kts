plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

kover {
    reports {
        verify {
            rule {
                // :core line coverage alone, so :app cannot mask a drop; measured 98.0% when set.
                minBound(85)
            }
        }
    }
}
