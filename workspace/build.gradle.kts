plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("xuanxing.android.library")
}

android {
    namespace = "me.rerere.workspace"
    // 显式固定 NDK 版本：否则 AGP 会在构建时自动下载默认版本，
    // 在 CI 上偶发下载包损坏（Error reading Zip content）导致整轮构建失败。
    // CI 的「预装 NDK」步骤安装的就是这个版本。
    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
