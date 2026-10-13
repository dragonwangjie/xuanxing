plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("xuanxing.android.library.compose")
}

android {
    namespace = "me.rerere.search"
}

dependencies {
    implementation(project(":ai"))
    implementation(project(":common"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    api(libs.jsoup)
    implementation(libs.quickjs)
    testImplementation(libs.junit)
}
