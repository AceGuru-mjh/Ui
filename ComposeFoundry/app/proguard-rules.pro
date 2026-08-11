# ComposeFoundry ProGuard Rules

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.foundry.preview.**$$serializer { *; }
-keepclassmembers class com.foundry.preview.** {
    *** Companion;
}
-keepclasseswithmembers class com.foundry.preview.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Core platform modules (pure data model + plugin contract, no Android deps)
-keep,includedescriptorclasses class com.foundry.core.uimodel.**$$serializer { *; }
-keep,includedescriptorclasses class com.foundry.core.plugin.**$$serializer { *; }
-keepclassmembers class com.foundry.core.uimodel.** {
    *** Companion;
}
-keepclassmembers class com.foundry.core.plugin.** {
    *** Companion;
}

# Jetpack Compose runtime: keep inline/Composable signatures used by remember delegates
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# =============================================
# Dynamic Plugin Loading: DexClassLoader + Reflection
# =============================================
# DynamicPluginManager uses DexClassLoader.loadClass() + getDeclaredConstructor().newInstance()
# to hot-load plugins at runtime. R8 may strip or obfuscate the no-arg constructors
# and entry-class metadata of UiFormatPlugin implementations, causing
# NoSuchMethodException / ClassNotFoundException in production builds.
#
# Strategy: keep all UiFormatPlugin implementations and their public constructors
# so the parent ClassLoader can resolve the plugin contract when the plugin's
# DexClassLoader attempts to instantiate them.

# Keep the plugin SDK contract interface itself (resolved by parent CL)
-keep interface com.foundry.core.plugin.UiFormatPlugin { *; }

# Keep all concrete UiFormatPlugin classes and their no-arg constructors
-keep class * extends com.foundry.core.plugin.UiFormatPlugin {
    <init>();
    <init>(...);
}

# Offline renderer plugins: SDK packs loaded via IRenderEngine / DexClassLoader in :renderer process
-keep interface com.foundry.core.renderer.IRenderEngine { *; }
-keep class * extends com.foundry.core.renderer.IRenderEngine {
    <init>();
    <init>(...);
}

# Local plugin hot-load entry point (com.foundry.plugin.LocalPlugin)
-keep class com.foundry.plugin.** { *; }

# ─────────────────────────────────────────────
# Plugin DEX raw preservation (packagePlugin builds)
# ─────────────────────────────────────────────
# When building DEX artifacts for the offline marketplace, these modules
# must NOT be minified/obfuscated because they will be loaded via DexClassLoader
# by other processes. Keep the entire plugin package structure.
-keep class com.foundry.plugin.simplejson.** { *; }
-keep class com.foundry.plugin.compose.** { *; }


