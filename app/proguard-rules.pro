# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line numbers for cleaner crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Retrofit + OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ─── Gson (used for non-history preferences) ──────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ─── Room Database ────────────────────────────────────────────────────────────
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# ─── App Model classes (Room entities, network responses) ─────────────────────
-keep class com.example.saharaa.model.** { *; }
-keep class com.example.saharaa.network.** { *; }

# ─── ML Kit ───────────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }

# ─── CameraX ──────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }