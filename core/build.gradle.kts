// What the phone app and the watch app share: the status page's models, what counts as a problem, the widget's and tile's
// content, the saved snapshot, and the API clients. Package dev.rootiest.status.data, as before the split.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.rootiest.status.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        // kotlinx.serialization's own keep rules come with it; nothing of ours needs keeping
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    testImplementation(libs.junit)
}
