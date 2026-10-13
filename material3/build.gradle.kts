plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("xuanxing.android.library.compose")
}

android {
    namespace = "me.rerere.rikkahub.material3"
    sourceSets {
        // 子模块 material-color-utilities 在本模块目录下（此前写成
        // "../material-color-utilities/java"，指向了不存在的仓库根路径）。
        getByName("main").java.srcDir("material-color-utilities/java")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
