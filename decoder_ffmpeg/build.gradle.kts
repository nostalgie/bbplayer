plugins {
    id("com.android.library")
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 34

    ndkVersion = "30.0.14904198"

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Configure native build with CMake
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1+"
        }
    }

    // Only build for ABIs that have FFmpeg libraries
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
}

dependencies {
    val media3Version = "1.2.1"
    implementation("androidx.media3:media3-decoder:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.annotation:annotation:1.7.1")
    compileOnly("org.checkerframework:checker-qual:3.42.0")
    compileOnly("org.jetbrains.kotlin:kotlin-annotations-jvm:1.9.22")
}
