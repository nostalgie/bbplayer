package com.dima.kidsvideoplayer.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages Lock Task Mode (Kiosk Mode) for the app.
 *
 * Prerequisites:
 * 1. App must be set as Device Owner via `adb shell dpm set-device-owner com.dima.kidsvideoplayer/.admin.MyDeviceAdminReceiver`
 *    OR Device Admin must be enabled manually + allowlisted by MDM.
 * 2. For development: use `adb shell dpm set-device-owner` on a fresh device (no accounts added).
 */
class LockTaskManager(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, MyDeviceAdminReceiver::class.java)
    }

    /**
     * Check if the app is set as Device Owner (required for programmatic startLockTask).
     */
    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Check if Device Admin is enabled (weaker than Device Owner, but still useful).
     */
    fun isAdminActive(): Boolean {
        return dpm.isAdminActive(adminComponent)
    }

    /**
     * Start Lock Task Mode (Kiosk Mode).
     * Must be called from Activity context.
     *
     * If the app is Device Owner, it uses the whitelist approach.
     * Otherwise, falls back to standard startLockTask() (requires user approval in Settings).
     */
    fun startKioskMode(activity: Activity) {
        try {
            if (isDeviceOwner()) {
                // Whitelist this activity for lock task
                dpm.setLockTaskPackages(
                    adminComponent,
                    arrayOf(context.packageName)
                )
                Log.d(TAG, "Lock task packages whitelisted (Device Owner)")
            }
            activity.startLockTask()
            Log.d(TAG, "Lock Task Mode STARTED")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Lock Task Mode", e)
        }
    }

    /**
     * Stop Lock Task Mode (exit Kiosk Mode).
     * Must be called from the same Activity that started it.
     */
    fun stopKioskMode(activity: Activity) {
        try {
            activity.stopLockTask()
            Log.d(TAG, "Lock Task Mode STOPPED")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Lock Task Mode", e)
        }
    }

    /**
     * Request Device Admin activation via system dialog.
     */
    fun requestDeviceAdmin(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Необходимо для режима киоска (блокировка ребёнка в приложении)"
            )
        }
        launcher.launch(intent)
    }

    /**
     * Get intent for Device Admin activation (alternative approach for startActivityForResult).
     */
    fun getDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Необходимо для режима киоска (блокировка ребёнка в приложении)"
            )
        }
    }

    companion object {
        private const val TAG = "LockTaskManager"
    }
}
