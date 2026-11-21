plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

kover {
    currentProject {
        // The "ci" variant exists in every module so the root can merge them; see the root build file.
        createVariant("ci") { add("jvm") }
    }
    reports {
        variant("ci") {
            verify {
                rule {
                    // :core line coverage alone, so :app cannot mask a drop; measured 98.0% when set.
                    minBound(85)
                }
            }
        }
    }
}

tasks.withType<Test> {
    // Half the cores, at least one, so several test JVMs run side by side without starving the machine.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
