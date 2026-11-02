import java.util.Properties

// AGP 9 compiles Kotlin itself: no org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The version scripts/release.sh tags (v<appVersion>). versionCode follows from it: 1.2.3 is 10203.
val appVersion = "1.3.0"
val (major, minor, patch) = appVersion.split(".").map(String::toInt)

// The release key's properties file (storeFile, storePassword, keyAlias, keyPassword), named by the Gradle property
// rootiest.signing in ~/.gradle/gradle.properties. Without it (CI, another machine) release builds get the debug key,
// which scripts/release.sh refuses to publish.
val releaseKey = providers.gradleProperty("rootiest.signing").orNull?.let { path ->
    Properties().apply { file(path).inputStream().use(::load) }
}

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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
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
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
