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
    // NDK 工具链（clang/llvm-strip 等）是 x86_64 host 二进制，无法在 aarch64
    // Linux 主机（如 ARM 服务器上的构建 sandbox）上执行，configureCMake 会直接失败。
    // 本工程里 CMake 的两个产物均无消费方：libworkspace.so 没有任何 external fun /
    // loadLibrary 引用；terminal-emulator AAR 自带 libtermux.so（且与本地同名，
    // packaging.pickFirsts 里已登记）。因此 ARM Linux 主机上跳过 CMake 构建，
    // 产物照旧由 AAR / jniLibs 提供；x86_64 主机与 CI 行为完全不变。
    val hostArch = System.getProperty("os.arch")?.lowercase().orEmpty()
    val hostOs = System.getProperty("os.name")?.lowercase().orEmpty()
    if (!(hostOs.contains("linux") && hostArch == "aarch64")) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
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
