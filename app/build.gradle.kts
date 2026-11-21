plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.juice"
    compileSdk = 36

    lint {
        warningsAsErrors = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
        // Needs the network and fails CI whenever a dependency releases; versions are bumped deliberately.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }

    defaultConfig {
        applicationId = "com.juice"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One debug key for every build, so a new APK from CI installs over the last one. CI runners
    // otherwise make a fresh debug key each run, and Android refuses an update signed differently.
    // A debug key is not a secret; release builds must not use it.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release key comes from the environment (see docs/releasing.md). Without it, packaging a
        // release APK fails (see the packageRelease check below); it never falls back to the debug key.
        create("release") {
            val keystorePath = System.getenv("JUICE_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("JUICE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JUICE_KEY_ALIAS")
                keyPassword = System.getenv("JUICE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Print a failing test's full message, so a CI log says why it failed.
        unitTests.all {
            it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
            // Half the cores, at least one, so several Robolectric JVMs run side by side.
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            it.maxHeapSize = "1g"
        }
    }

    sourceSets {
        // Exported Room schemas ride along as debug assets (AGP does not merge the test source set's assets into JVM unit tests),
        // so MigrationTestHelper can load them under Robolectric. Debug only, so the release APK ships no schema JSON.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// A release APK must be signed with the release keystore, never the committed debug one.
// Checked at packaging time so tests, lint and coverage still run without the key.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        if (System.getenv("JUICE_KEYSTORE_FILE").isNullOrBlank()) {
            throw GradleException("JUICE_KEYSTORE_FILE is not set: release builds must be signed with the release keystore, see docs/releasing.md")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.work.testing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

kover {
    currentProject {
        // Only the debug variant is unit-tested in CI, so coverage must not wait on testReleaseUnitTest.
        createVariant("ci") { add("debug") }
    }
    reports {
        variant("ci") {
            verify {
                rule {
                    // :app line coverage alone, so :core cannot mask a drop; measured 75.3% when set.
                    minBound(60)
                }
            }
        }
    }
}
