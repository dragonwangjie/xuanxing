plugins {
    alias(libs.plugins.android.library)
    id("xuanxing.android.library")
}

android {
    namespace = "me.rerere.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    api(libs.androidx.core.ktx)
}
