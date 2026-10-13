// 玄星：build-logic 模块构建文件（同步自 RikkaHub 架构）
// 注册 Convention Plugin，统一管理所有库模块的构建配置
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "xuanxing.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "xuanxing.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
    }
}