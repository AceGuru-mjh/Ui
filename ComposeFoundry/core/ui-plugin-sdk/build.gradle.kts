plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core:ui-model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // ParseResult / UiArtifact 等作为插件契约交换格式可序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // 单元测试（纯 JVM）
    testImplementation("junit:junit:4.13.2")
}
