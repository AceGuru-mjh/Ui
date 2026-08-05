plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")
}

kotlin {
    jvmToolchain(17)
}
