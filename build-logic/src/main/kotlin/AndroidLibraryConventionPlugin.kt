import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * 玄星 Convention Plugin：统一所有 Android 库模块的构建配置。
 *
 * 同步自 RikkaHub build-logic 模块，消除 9 个库模块中重复的：
 * - compileSdk / minSdk / compileOptions (JDK 统一为 17)
 * - Kotlin compilerOptions opt-in 声明
 * - targetSdk 统一
 *
 * 注意：本 plugin 不自行 apply "com.android.library" / "org.jetbrains.kotlin.android"，
 * 而是要求子模块在 plugins{} 块中用 alias(libs.plugins.android.library) 声明。
 * 这样 Gradle 的类型安全访问器（android {}）才能正常生成。
 * 本 plugin 只负责配置已应用插件的扩展。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<com.android.build.api.dsl.LibraryExtension> {
                compileSdk = 37

                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }

                // 玄星改进：所有库模块 JDK 统一为 17（RikkaHub 也有此不一致，玄星修复之）
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }
            }

            // 统一 Kotlin 编译选项与 opt-in
            tasks.withType(KotlinCompile::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    optIn.add("kotlin.uuid.ExperimentalUuidApi")
                    optIn.add("kotlin.time.ExperimentalTime")
                    optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
                }
            }
        }
    }
}