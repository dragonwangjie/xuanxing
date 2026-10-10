// 国内镜像开关：默认开启（本机开发加速）。
// CI（GitHub Actions 的海外 runner）必须关掉：aliyun 镜像对较新的构件
// （如 com.google.devtools.ksp:...gradle.plugin:2.3.4）会返回坏数据，
// 导致 runner 上报 "Plugin was not found in any of the following sources"。
// 置 XUANXING_CN_MIRROR=0 即只用官方仓库，release.yml 已自动设置。
pluginManagement {
    repositories {
        // 国内镜像优先，加速插件下载
        if (System.getenv("XUANXING_CN_MIRROR") != "0") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.itextsupport.com/android")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像优先，加速依赖下载（原始仓库作为回退保留在后面）
        if (System.getenv("XUANXING_CN_MIRROR") != "0") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
    }
}

rootProject.name = "rikkahub"
include(":app")
include(":highlight")
include(":ai")
include(":search")
include(":speech")
include(":common")
include(":document")
include(":web")
include(":material3")
include(":workspace")
include(":app:baselineprofile")
