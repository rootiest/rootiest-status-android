// The Wear OS app: a tile with the overall status and what is wrong, and a screen with the full list. It gets each snapshot
// from the phone app over the Wearable Data Layer, so it needs no network or sign-in of its own (docs/README.md, "Wear OS").
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// the version and the release key, shared with the phone app (the root build.gradle.kts)
val appVersion = rootProject.extra["appVersion"] as String
val releaseKey = rootProject.extra["releaseKey"] as java.util.Properties?

android {
    namespace = "dev.rootiest.status.wear"
    compileSdk = 37

    defaultConfig {
        // the phone app's: the Data Layer only connects apps with the same package name and key
        applicationId = "dev.rootiest.status"
        minSdk = 30
        targetSdk = 37
        versionCode = rootProject.extra["watchVersionCode"] as Int
        versionName = "$appVersion-watch"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    // the screen tests run on the JVM under Robolectric, as the phone app's do
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED") }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}

// The golden images live with the tests and are committed; diffs from a failed verify go to build/outputs/roborazzi.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
