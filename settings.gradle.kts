/*
 * 项目的全局构建设置
 *
 * 这个文件控制整个项目（不只是 app 模块）的构建行为，主要包括：
 * 1. 插件仓库：去哪里下载 Android Gradle Plugin 等构建插件
 * 2. 依赖仓库：去哪里下载 App 用的第三方库
 * 3. 模块声明：项目包含哪些子模块
 */

pluginManagement {
    /*
     * 插件仓库：Gradle 构建插件从这里下载
     *
     * 插件是构建工具本身需要的（如 com.android.application），
     * 不是 App 运行时用的依赖库
     */
    repositories {
        // Google 仓库：存放 Android 构建插件
        google {
            content {
                // 只从 Google 仓库下载 Android / Google / AndroidX 相关的包
                // 其他包走下面的 mavenCentral，避免搜索太多仓库浪费时间
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven 中央仓库：全球最大的 Java/Kotlin 依赖仓库
        mavenCentral()
        // Gradle 官方插件门户：存放 Gradle 官方插件
        gradlePluginPortal()
    }
}

// Foojay 工具链解析器：自动帮 Gradle 找到本机安装的 JDK
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    /*
     * FAIL_ON_PROJECT_REPOS：强制所有模块统一使用下面声明的仓库
     * 不允许单个模块自己另开仓库，保证统一管理
     */
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    /*
     * 依赖仓库：App 运行时依赖的第三方库从这里下载
     *
     * Gradle 会按顺序在这些仓库中搜索：
     *   google() → mavenCentral() → JitPack
     * 找到就停止，找不到就报错
     */
    repositories {
        // Google 仓库：存放 AndroidX、Material 等官方库
        google()
        // 阿里云 Maven 镜像，国内下载依赖走这条线，比 mavenCentral 快很多
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // Maven 中央仓库：存放通用 Java/Android 库
        mavenCentral()
        // JitPack 仓库：存放 GitHub 上的开源 Android 库
        // 比如 MPAndroidChart 就是托管在 JitPack 上的
        maven { url = uri("https://jitpack.io") }
    }

}

// 项目名称
rootProject.name = "MyStepCounter"

// 声明项目包含的子模块，目前只有一个 app 模块
include(":app")
