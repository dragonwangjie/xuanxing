plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("xuanxing.android.library")
}

android {
    namespace = "me.rerere.common"
}

dependencies {
    // kotlinx
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    api(libs.androidx.core.ktx)

    // okhttp（供 :ai / :speech 等模块经由本模块 api 传递使用）
    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.okhttp.logging)

    // apache commons
    api(libs.commons.text)

    // floating window
    // https://github.com/Petterpx/FloatingX
    api(libs.floatingx)
    api(libs.floatingx.compose)

    // quickjs
    api(libs.quickjs)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
