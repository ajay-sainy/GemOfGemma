# ── LiteRT-LM (Gemma inference engine) ────────────────────────
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# ── Hilt / Dagger ─────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── kotlinx.serialization ────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gemofgemma.**$$serializer { *; }
-keepclassmembers class com.gemofgemma.** {
    *** Companion;
}
-keepclasseswithmembers class com.gemofgemma.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Gson (used by LiteRT-LM internally) ──────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Data classes used in serialization ────────────────────────
-keep class com.gemofgemma.core.model.** { *; }
-keep class com.gemofgemma.ai.tools.PhoneActionToolSet$ToolDefinition { *; }
-keep class com.gemofgemma.ai.tools.PhoneActionToolSet$ToolParameter { *; }
