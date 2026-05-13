package com.dima.kidsvideoplayer.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Admin Receiver required for Lock Task Mode (Kiosk Mode).
 *
 * To activate: Settings → Security → Device Admin → Enable "Детский Видеоплеер"
 * Or programmatically via DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin disabled")
    }

    companion object {
        private const val TAG = "MyDeviceAdminReceiver"
    }
}
