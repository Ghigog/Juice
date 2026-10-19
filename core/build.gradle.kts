plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
