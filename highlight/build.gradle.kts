plugins {
    alias(libs.plugins.android.library)
    id("xuanxing.android.library.compose")
}

android {
    namespace = "me.rerere.rikkahub.highlight"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
}
