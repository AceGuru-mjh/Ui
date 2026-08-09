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

