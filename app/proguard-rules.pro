# ExoPlayer rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# FFmpeg extension native methods
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
