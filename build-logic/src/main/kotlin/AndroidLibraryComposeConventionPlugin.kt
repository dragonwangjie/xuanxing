import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * 玄星 Convention Plugin：为需要 Compose 的库模块统一配置 Compose 构建特性。
 *
 * 同步自 RikkaHub build-logic 模块。
 * 使用：在需要 Compose 的子模块中叠加 `id("xuanxing.android.library.compose")`
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}