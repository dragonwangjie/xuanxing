import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * 玄星 Convention Plugin：为需要 Compose 的库模块统一配置 Compose 构建特性。
 *
 * 同步自 RikkaHub build-logic 模块。
 * 注意：本 plugin 不自行 apply compose 插件，
 * 而是要求子模块在 plugins{} 块中用 alias(libs.plugins.kotlin.compose) 声明。
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}