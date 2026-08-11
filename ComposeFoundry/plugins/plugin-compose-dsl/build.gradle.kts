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

// ═══════════════════════════════════════════════════════════
//  packagePlugin — DEX-ify → DexClassLoader 可加载的 JAR
//  Usage: ./gradlew :plugins:plugin-compose-dsl:packagePlugin
// ═══════════════════════════════════════════════════════════
tasks.register("packagePlugin") {
    group = "foundry"
    description = "DEX-ify plugin + runtime deps → DexClassLoader-loadable JAR"
    val packDir = layout.buildDirectory.dir("foundry-pack")
    dependsOn("assembleDebug")
    outputs.dir(packDir)

    doLast {
        val aarJar = file("build/intermediates/aar_main_jar/debug/classes.jar")
        if (!aarJar.exists()) error("classes.jar not found; run assembleDebug first")
        val packJson = file("foundry-pack.json")
        if (!packJson.exists()) error("foundry-pack.json not found")

        // runtime deps: debugRuntimeClasspath = implementation + runtimeOnly (excludes compileOnly)
        val rtJars = configurations["debugRuntimeClasspath"].resolvedConfiguration
            .resolvedArtifacts.map { it.file }.filter { it.exists() && it.extension == "jar" }

        val sdkDir = android.sdkDirectory
        val btVer = android.buildToolsVersion
        val d8 = File(sdkDir, "build-tools/$btVer/d8${if (org.gradle.internal.os.OperatingSystem.current().isWindows) ".bat" else ""}")
        if (!d8.exists()) error("d8 not found: $d8")

        val out = packDir.get().asFile; out.deleteRecursively(); out.mkdirs()
        val dexJar = File(out, "classes.jar")
        val androidJar = File(sdkDir, "platforms/android-34/android.jar")

        val proc = ProcessBuilder().command(
            mutableListOf(d8.absolutePath, "--release", "--output", dexJar.absolutePath).also { args ->
                if (androidJar.exists()) { args.add("--lib"); args.add(androidJar.absolutePath) }
                (rtJars.map { it.absolutePath } + aarJar.absolutePath).forEach { args.add(it) }
            }
        ).directory(project.buildDir).redirectErrorStream(true).start()
        val txt = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "d8 failed:\n$txt" }

        packJson.copyTo(File(out, "foundry-pack.json"), overwrite = true)
        logger.lifecycle("[plugin-compose-dsl] packaged → $out (${dexJar.length()} bytes)")
    }
}
