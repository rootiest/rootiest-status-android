import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.roborazzi) apply false
}

// The version scripts/release.sh tags (v<appVersion>), for the phone app and the watch app alike. versionCode follows from it:
// 1.2.3 is 10203.
extra["appVersion"] = "1.17.1"

// The release key's properties file (storeFile, storePassword, keyAlias, keyPassword), named by the Gradle property
// rootiest.signing in ~/.gradle/gradle.properties. Without it (CI, another machine) release builds get the debug key,
// which scripts/release.sh refuses to publish. Both apps are signed with it: the Wearable Data Layer only connects
// the phone app and the watch app when they share the package name and the key.
extra["releaseKey"] = providers.gradleProperty("rootiest.signing").orNull?.let { path ->
    Properties().apply { file(path).inputStream().use(::load) }
}
