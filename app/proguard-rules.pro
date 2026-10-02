# Keep metadata needed by Kotlin serialization and JSON backup compatibility.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# Backup JSON field names must remain stable across versions.
-keep class com.bookmanager.core.domain.model.** { *; }

# Main Android components are referenced from the manifest.
-keep class com.bookmanager.MainActivity { *; }
