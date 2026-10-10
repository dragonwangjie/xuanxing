import org.apache.tools.ant.taskdefs.condition.Os

// 注意：必须用 plugins{} 声明 Android 插件。
// 之前这里写的是 apply(plugin = "com.android.library")，但本脚本后面仍然使用了
// android { } 类型安全访问器 —— Kotlin DSL 只会为 plugins{} 块里声明的插件生成
// 访问器，用 apply() 应用时不会生成，配置阶段会直接 "Unresolved reference: android"。
plugins {
    alias(libs.plugins.android.library)
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webStaticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")

// 跳过前端构建的开关：-Pxuanxing.skipWebUi=true
// 仓库里已经提交了 web/src/main/resources/static 的构建产物，只想编译原生部分时
// （机器上没有 Node/pnpm，或前端依赖装不动）可以用它跳过 pnpm 构建，直接复用已提交资源。
val skipWebUi = (findProperty("xuanxing.skipWebUi")?.toString()?.toBooleanStrictOrNull() == true)
    || System.getenv("XUANXING_SKIP_WEB_UI")?.toBooleanStrictOrNull() == true

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "Build web-ui and copy its static output into the web module resources."

    enabled = !skipWebUi

    workingDir = webUiDir.asFile
    when {
        Os.isFamily(Os.FAMILY_MAC) -> commandLine("zsh", "-ic", "pnpm run build")
        Os.isFamily(Os.FAMILY_WINDOWS) -> commandLine("cmd", "/c", "pnpm run build")
        else -> commandLine("pnpm", "run", "build")
    }

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("copy.ts"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))
    outputs.dir(webStaticResourcesDir)
}

android {
    namespace = "me.rerere.rikkahub.web"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("preBuild") {
    // 仅在未跳过前端构建时才把 preBuild 挂到 buildWebUi 上，
    // 否则任务被 disabled 后依赖关系会让 Gradle 直接报错。
    if (!skipWebUi) {
        dependsOn(buildWebUi)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ktor server
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.cors)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.cio)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
