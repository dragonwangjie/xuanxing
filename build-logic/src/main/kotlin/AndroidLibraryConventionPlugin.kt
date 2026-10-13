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
 * 使用：在子模块 build.gradle.kts 中 `plugins { id("xuanxing.android.library") }`
 * 替代手动写 android {} 配置块。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

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