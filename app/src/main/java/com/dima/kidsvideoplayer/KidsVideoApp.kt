package com.dima.kidsvideoplayer

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KidsVideoApp initialized")
    }

    companion object {
        private const val TAG = "KidsVideoApp"
    }
}
