pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ComposeFoundry"
include(":app")
include(":foundry-gradient")
include(":foundry-animation")
include(":foundry-codegen")
include(":foundry-a11y")

// 平台核心层：规范化 UI 图 + 插件化格式系统（不依赖 Android）
include(":core:ui-model")
include(":core:ui-plugin-sdk")
// 多进程渲染通信接口（AIDL + Parcelable 模型）
include(":core:renderer-api")
// 离线渲染插件市场（独立模块，compileOnly 依赖 core:renderer-api）
include(":plugins:plugin-simple-json")
// Compose JSON DSL 渲染引擎（基于 Compose 的离线渲染插件）
include(":plugins:plugin-compose-dsl")
include(":plugins:plugin-version-printer")
