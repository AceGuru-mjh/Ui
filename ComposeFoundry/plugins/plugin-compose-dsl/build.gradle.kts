plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.foundry.plugin.composedsl"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ⚠️ compileOnly：编译时可见，打包不包含
    // 运行时由父 ClassLoader（app 的 CL）提供 Compose 运行时
    compileOnly(project(":core:renderer-api"))
    compileOnly(platform("androidx.compose:compose-bom:2024.09.00"))
    compileOnly("androidx.compose.ui:ui")
    compileOnly("androidx.compose.ui:ui-graphics")
    compileOnly("androidx.compose.foundation:foundation")
    compileOnly("androidx.compose.material3:material3")
    compileOnly("androidx.core:core-ktx:1.12.0")

    // 插件自有依赖（打包进 JAR，与主程序隔离）
    implementation("com.google.code.gson:gson:2.10.1")
}
