# ==============================
# Media3 / ExoPlayer
# ==============================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# FFmpeg extension native methods
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# ==============================
# DataStore Preferences
# ==============================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ==============================
# Kotlin Coroutines
# ==============================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile **;
}
-keepclassmembers class * extends kotlin.coroutines.Continuation { *; }
-dontwarn kotlinx.coroutines.**

# ==============================
# Jetpack Compose
# ==============================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    *** get*();
    void set*(***);
}
-keepclassmembers class * extends androidx.compose.runtime.Composer {
    *** get*();
    void set*(***);
}

# ==============================
# Navigation Compose
# ==============================
-keepnames class androidx.navigation.**
-keep class * extends androidx.navigation.NavDestination { *; }

# ==============================
# AndroidX / Support Library
# ==============================
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-dontwarn androidx.lifecycle.**
