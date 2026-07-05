package com.dima.kidsvideoplayer

import android.app.Application
import android.content.ComponentName
import android.util.Log
import com.dima.kidsvideoplayer.data.VideoLibraryService
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager

/**
 * Application class for KidsVideoPlayer.
 * Holds process-wide singletons that must survive Activity recreation
 * (critical for HOME/kiosk launcher mode).
 */
class KidsVideoApp : Application() {

    val videoRepository: VideoRepository by lazy {
        VideoRepository(this)
    }

    val videoLibraryService: VideoLibraryService by lazy {
        VideoLibraryService(this, videoRepository)
    }

    /** Single libVLC player instance for the process — do not release on Activity.onDestroy. */
    val videoPlayerManager: VideoPlayerManager by lazy {
        VideoPlayerManager(this)
    }

    private val kioskPrefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    /**
     * True after parent taps "Выход" — blocks kiosk auto-restart until the app icon is tapped.
     * Persisted so a HOME relaunch after process death still redirects to the system launcher.
     */
    var suspendedFromKiosk: Boolean
        get() = kioskPrefs.getBoolean(KEY_SUSPENDED, false)
        set(value) {
            kioskPrefs.edit().putBoolean(KEY_SUSPENDED, value).apply()
        }

    /** Cached system launcher used on exit and for HOME redirects while suspended. */
    var cachedExternalLauncher: ComponentName?
        get() = kioskPrefs.getString(KEY_EXTERNAL_LAUNCHER, null)
            ?.let(ComponentName::unflattenFromString)
        set(value) {
            kioskPrefs.edit()
                .putString(KEY_EXTERNAL_LAUNCHER, value?.flattenToString())
                .apply()
        }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KidsVideoApp initialized")
    }

    companion object {
        private const val TAG = "KidsVideoApp"
        private const val PREFS_NAME = "kiosk_state"
        private const val KEY_SUSPENDED = "suspended_from_kiosk"
        private const val KEY_EXTERNAL_LAUNCHER = "external_launcher"
    }
}
