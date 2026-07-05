# ==============================
# libVLC
# ==============================
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

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
