plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("xuanxing.android.library.compose")
}

android {
    namespace = "me.rerere.rikkahub.highlight"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
}
