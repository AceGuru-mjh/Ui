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
