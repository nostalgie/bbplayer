package com.dima.kidsvideoplayer.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

/**
 * Launches the system home screen, avoiding our own HOME activity when possible.
 */
object LauncherHelper {

    private const val TAG = "LauncherHelper"

    fun isHomeOnlyIntent(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_MAIN) return false
        return intent.hasCategory(Intent.CATEGORY_HOME) &&
            !intent.hasCategory(Intent.CATEGORY_LAUNCHER)
    }

    /**
     * Pick the first HOME handler that is not [ownPackageName].
     * Exposed for unit tests.
     */
    fun findExternalLauncherComponent(
        resolveInfos: List<ResolveInfo>,
        ownPackageName: String
    ): ComponentName? {
        val candidate = resolveInfos.firstOrNull {
            it.activityInfo.packageName != ownPackageName
        } ?: return null
        return ComponentName(
            candidate.activityInfo.packageName,
            candidate.activityInfo.name
        )
    }

    /**
     * Launch the system home screen. Returns the component that was started, if any.
     */
    fun launchSystemHome(
        activity: Activity,
        preferredComponent: ComponentName? = null
    ): ComponentName? {
        val ownPackage = activity.packageName
        val external = when {
            preferredComponent != null && preferredComponent.packageName != ownPackage ->
                preferredComponent
            else -> findExternalLauncherFromPackageManager(activity, ownPackage)
        }
        val launchIntent = if (external != null) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                component = external
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        activity.startActivity(launchIntent)
        Log.d(TAG, "Launched system home: ${external ?: "chooser fallback"}")
        return external
    }

    private fun findExternalLauncherFromPackageManager(
        activity: Activity,
        ownPackageName: String
    ): ComponentName? {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = activity.packageManager.queryIntentActivities(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return findExternalLauncherComponent(resolveInfos, ownPackageName)
    }
}
