package com.dima.kidsvideoplayer

import android.app.Application
import android.util.Log

/**
 * Application class for KidsVideoPlayer.
 * Currently used only for initialization logging.
 * Can be extended for DI setup, DataStore initialization, etc.
 */
class KidsVideoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KidsVideoApp initialized")
    }

    companion object {
        private const val TAG = "KidsVideoApp"
    }
}
