package com.dima.kidsvideoplayer.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
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
     * Check if the app is currently in Lock Task Mode.
     */
    fun isLockTaskRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager
        return activityManager?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }

    /**
     * Check if screen pinning is enabled in device Settings.
     * Required for screen pinning fallback when app is NOT Device Owner.
     *
     * Path: Settings → Security → Screen pinning → Enable
     */
    fun isScreenPinningEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                "lock_to_app_enabled",  // Hidden setting name for screen pinning
                0
            ) == 1
        } catch (e: Exception) {
            // Fallback: assume enabled, try anyway
            Log.w(TAG, "Could not check screen pinning setting", e)
            true
        }
    }

    /**
     * Start Lock Task Mode (Kiosk Mode).
     * Must be called from Activity context.
     *
     * If the app is Device Owner, it uses the whitelist approach (fully automatic).
     * Otherwise, falls back to screen pinning via startLockTask()
     * (requires "Screen pinning" to be enabled in Settings → Security).
     */
    fun startKioskMode(activity: Activity): Boolean {
        return try {
            if (isDeviceOwner()) {
                // Whitelist this activity for lock task
                dpm.setLockTaskPackages(
                    adminComponent,
                    arrayOf(context.packageName)
                )
                Log.d(TAG, "Lock task packages whitelisted (Device Owner)")
            } else {
                Log.d(TAG, "Not Device Owner — using screen pinning fallback")
                if (!isScreenPinningEnabled()) {
                    Log.w(TAG, "Screen pinning is not enabled in Settings! " +
                            "Go to Settings → Security → Screen pinning to enable it.")
                }
            }
            activity.startLockTask()
            Log.d(TAG, "Lock Task Mode STARTED")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Lock Task Mode. " +
                    "Make sure Screen pinning is enabled in Settings → Security", e)
            false
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
     * Apply kiosk-related device policies when app is Device Owner.
     * These policies lock down the device for child-safe usage:
     * - Disable status bar (prevents pulling down notifications)
     * - Set our app as the default launcher
     * - Disable keyguard (no lock screen)
     * - Enable stay-on-while-plugged-in
     */
    fun applyKioskPolicies() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot apply kiosk policies — app is not Device Owner")
            return
        }

        try {
            // Disable status bar — prevents child from pulling down notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setStatusBarDisabled(adminComponent, true)
                Log.d(TAG, "Status bar disabled")
            }

            // Set our app as the default activity for HOME intent
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                adminComponent,
                homeFilter,
                ComponentName(context.packageName, "com.dima.kidsvideoplayer.MainActivity")
            )
            Log.d(TAG, "Set as default HOME activity")

            // Disable lock screen / keyguard
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setKeyguardDisabled(adminComponent, true)
                Log.d(TAG, "Keyguard disabled")
            }

            // Keep device awake while plugged in (1=AC, 2=USB, 4=Wireless)
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB
            )
            Log.d(TAG, "Stay-on-while-plugged-in enabled")

            Log.d(TAG, "All kiosk policies applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply kiosk policies", e)
        }
    }

    /**
     * Remove all kiosk policies and restore device to normal state.
     * Call this before exiting kiosk mode or when the parent wants to disable it.
     */
    fun removeKioskPolicies() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot remove kiosk policies — app is not Device Owner")
            return
        }

        try {
            // Re-enable status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setStatusBarDisabled(adminComponent, false)
                Log.d(TAG, "Status bar re-enabled")
            }

            // Remove persistent preferred HOME activity
            dpm.clearPackagePersistentPreferredActivities(
                adminComponent,
                context.packageName
            )
            Log.d(TAG, "Default HOME activity cleared")

            // Re-enable keyguard
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setKeyguardDisabled(adminComponent, false)
                Log.d(TAG, "Keyguard re-enabled")
            }

            Log.d(TAG, "All kiosk policies removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove kiosk policies", e)
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
