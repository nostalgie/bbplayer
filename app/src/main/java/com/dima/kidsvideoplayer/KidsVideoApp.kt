package com.dima.kidsvideoplayer

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class KidsVideoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KidsVideoApp initialized")
    }

    companion object {
        private const val TAG = "KidsVideoApp"
    }
}
