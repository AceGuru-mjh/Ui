plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.foundry.plugin.simplejson"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ⚠️ 核心魔法：使用 compileOnly！
    // 编译时能看到 IRenderEngine，但打包成 JAR 时不会打进去。
    // 运行时，插件会从父 ClassLoader（渲染进程 CL）中找到 IRenderEngine，实现完美对接。
    compileOnly(project(":core:renderer-api"))

    // 插件自己独有的依赖（会被打包进最终 JAR 中，与主程序隔离）
    implementation("com.google.code.gson:gson:2.10.1")
}
