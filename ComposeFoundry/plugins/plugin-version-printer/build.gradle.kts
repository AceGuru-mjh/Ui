plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.foundry.plugin.version"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.foundry.plugin.version"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-hotfix"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // ⚠️ 核心魔法：compileOnly！
    // 编译时能看到 UiFormatPlugin / UiGraph 等契约，但打包进 APK 时不会把它们打进去。
    // 运行时，插件通过父 ClassLoader（主进程 CL）找到这些类，实现契约对接。
    // 注意：core:ui-plugin-sdk 用 implementation 依赖 core:ui-model，不对外传递，
    // 因此插件必须显式 compileOnly ui-model，才能访问 UiNode / UiGraph / UiValue。
    compileOnly(project(":core:ui-plugin-sdk"))
    compileOnly(project(":core:ui-model"))
}
