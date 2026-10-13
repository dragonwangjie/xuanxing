import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * 玄星 Convention Plugin：为需要 Compose 的库模块统一配置 Compose 构建特性。
 *
 * 同步自 RikkaHub build-logic 模块。
 * 注意：本 plugin 不自行 apply compose 编译器插件，
 * 而是要求子模块在 plugins{} 块中用 alias(libs.plugins.kotlin.compose) 声明；
 * 但会自动叠加基础 convention（xuanxing.android.library），
 * 这样只声明本 plugin 的模块同样能拿到 compileSdk / minSdk / JDK / opt-in 配置。
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("xuanxing.android.library")
            }
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}