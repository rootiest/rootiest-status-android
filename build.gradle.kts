import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.roborazzi) apply false
}

// The version scripts/release.sh tags (v<appVersion>), for the phone app and the watch app alike.
extra["appVersion"] = "1.19.0"

// Each versionCode follows from it: major, two digits of minor, two of patch, then one for the kind of device, so 1.2.3 is 102030 on
// the phone and 102031 on the watch. Google Play needs the two to differ, and gives a device the highest code it can run, so the
// watch's is the higher one. Minor and patch stay under 100, or a later version could get a lower code.
val (major, minor, patch) = (extra["appVersion"] as String).split(".").map(String::toInt)
require(minor < 100 && patch < 100) { "the versionCode has two digits each for minor and patch" }
extra["phoneVersionCode"] = (major * 10000 + minor * 100 + patch) * 10
extra["watchVersionCode"] = (major * 10000 + minor * 100 + patch) * 10 + 1

// The release key's properties file (storeFile, storePassword, keyAlias, keyPassword), named by the Gradle property
// rootiest.signing in ~/.gradle/gradle.properties. Without it (CI, another machine) release builds get the debug key,
// which scripts/release.sh refuses to publish. Both apps are signed with it: the Wearable Data Layer only connects
// the phone app and the watch app when they share the package name and the key.
extra["releaseKey"] = providers.gradleProperty("rootiest.signing").orNull?.let { path ->
    Properties().apply { file(path).inputStream().use(::load) }
}
