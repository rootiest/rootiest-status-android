// AGP 9 compiles Kotlin itself: no org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Settings > About > Open-source licenses: the dependencies and their licenses, collected at build time
    alias(libs.plugins.aboutlibraries)
    // screenshot tests: recordRoborazziDebug saves the golden images, verifyRoborazziDebug compares against them
    alias(libs.plugins.roborazzi)
}

// the version and the release key, shared with the watch app (the root build.gradle.kts)
val appVersion = rootProject.extra["appVersion"] as String
val (major, minor, patch) = appVersion.split(".").map(String::toInt)
val releaseKey = rootProject.extra["releaseKey"] as java.util.Properties?

android {
    namespace = "dev.rootiest.status"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.rootiest.status"
        minSdk = 29
        targetSdk = 37
        versionCode = major * 10000 + minor * 100 + patch
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // install only for the main user: adb's default is every profile, and a stale copy in a Private space blocks
    // installs signed with another key (Obtainium's "failureConflict")
    installation {
        installOptions += listOf("--user", "0")
    }

    // Compose UI tests run on the JVM under Robolectric, which needs the app's resources (drawables, strings)
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into the JDK's file descriptors, which JDK 25 no longer allows by default (CI's 21 does)
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED") }
    }

    // the JSON fixtures (the hub's real answers) live with :core's tests, which use them too
    sourceSets.getByName("test").resources.directories.add("../core/src/test/resources")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.unifiedpush.connector)
    implementation(libs.okhttp)
    // each snapshot goes to the watch app over the Wearable Data Layer (Watch.kt)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    // support ticket messages are Markdown: parsed here, drawn as styled text (ui/Markdown.kt)
    implementation(libs.commonmark)
    implementation(libs.aboutlibraries.compose.m3)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // newer than the one ui-test brings, whose idling check calls an InputManager method Android 16 removed
    testImplementation(libs.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    // the empty activity createComposeRule() starts
    debugImplementation(libs.compose.ui.test.manifest)
}

// The golden images live with the tests and are committed; diffs from a failed verify go to build/outputs/roborazzi.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
