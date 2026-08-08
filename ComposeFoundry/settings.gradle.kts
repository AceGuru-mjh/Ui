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
