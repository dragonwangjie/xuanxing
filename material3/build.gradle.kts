plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("xuanxing.android.library.compose")
}

android {
    namespace = "me.rerere.rikkahub.material3"
    sourceSets {
        // 子模块 material-color-utilities 就在本模块目录下（重构时误写成
        // "../material-color-utilities/java"，路径与源语言都不对）；
        // 上游与本仓库初始导入用的都是 kotlin 目录。
        getByName("main").kotlin.srcDir("material-color-utilities/kotlin")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
